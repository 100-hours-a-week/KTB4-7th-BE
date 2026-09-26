package com.memme.service.chat;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.memme.dto.chat.ChatAiRequest;
import com.memme.dto.chat.ChatHistoryResponse;
import com.memme.dto.chat.ChatMessageRequest;
import com.memme.entity.chat.ChatMessageEntity;
import com.memme.entity.chat.ChatMessageRole;
import com.memme.entity.chat.ChatMessageStatus;
import com.memme.entity.solution.SolutionBundleEntity;
import com.memme.entity.solution.SolutionBundleStatus;
import com.memme.entity.solution.SolutionEntity;
import com.memme.exception.chat.ChatAiException;
import com.memme.exception.chat.ChatInsufficientDataException;
import com.memme.exception.chat.ChatRequestException;
import com.memme.repository.chat.ChatMessageRepository;
import com.memme.repository.solution.SolutionBundleRepository;
import com.memme.repository.solution.SolutionRepository;
import com.memme.repository.store.StoreOwnershipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static com.memme.exception.chat.ChatRequestException.Reason.GENERATION_IN_PROGRESS;
import static com.memme.exception.chat.ChatRequestException.Reason.INVALID_CONTENT;
import static com.memme.exception.chat.ChatRequestException.Reason.RETRY_TARGET_NOT_FOUND;
import static com.memme.exception.chat.ChatRequestException.Reason.SOLUTION_NOT_READY;
import static com.memme.exception.chat.ChatRequestException.Reason.STORE_OWNER_REQUIRED;

@Service
public class ChatService {

    private static final String SERVICE_GUIDE =
            "매출과 오늘의 솔루션을 바탕으로 운영 질문에 답변해드려요.";
    private static final List<String> RECOMMENDED_QUESTIONS = List.of(
            "오늘 매출을 높이려면 무엇을 해야 하나요?",
            "어떤 시간대에 프로모션을 진행하면 좋을까요?"
    );
    private static final List<ChatMessageStatus> ACTIVE_STATUSES = List.of(
            ChatMessageStatus.PENDING,
            ChatMessageStatus.STREAMING
    );

    private final ChatMessageRepository chatMessageRepository;
    private final SolutionBundleRepository bundleRepository;
    private final SolutionRepository solutionRepository;
    private final StoreOwnershipRepository ownershipRepository;
    private final ChatAiClient aiClient;
    private final ChatMessagePersistenceService persistenceService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ChatService(
            ChatMessageRepository chatMessageRepository,
            SolutionBundleRepository bundleRepository,
            SolutionRepository solutionRepository,
            StoreOwnershipRepository ownershipRepository,
            ChatAiClient aiClient,
            ChatMessagePersistenceService persistenceService,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.chatMessageRepository = chatMessageRepository;
        this.bundleRepository = bundleRepository;
        this.solutionRepository = solutionRepository;
        this.ownershipRepository = ownershipRepository;
        this.aiClient = aiClient;
        this.persistenceService = persistenceService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ChatHistoryResponse getToday(Long userId, Long storeId) {
        requireOwnership(userId, storeId);
        LocalDate today = LocalDate.now(clock);
        List<ChatHistoryResponse.Message> messages = chatMessageRepository
                .findAllByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAscIdAsc(
                        userId,
                        startOf(today),
                        startOf(today.plusDays(1))
                ).stream()
                .map(this::toResponse)
                .toList();
        return new ChatHistoryResponse(
                messages.isEmpty() ? "오늘 대화가 없습니다." : "조회에 성공했습니다.",
                new ChatHistoryResponse.Data(
                        today,
                        SERVICE_GUIDE,
                        messages,
                        messages.isEmpty() ? RECOMMENDED_QUESTIONS : List.of()
                )
        );
    }

    @Transactional
    public ChatStreamPlan prepare(Long userId, Long storeId, ChatMessageRequest request) {
        requireOwnership(userId, storeId);
        String question = validateContent(request == null ? null : request.content());
        LocalDate today = LocalDate.now(clock);
        LocalDateTime from = startOf(today);
        LocalDateTime to = startOf(today.plusDays(1));
        if (chatMessageRepository
                .existsByUserIdAndRoleAndStatusInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        userId,
                        ChatMessageRole.ASSISTANT,
                        ACTIVE_STATUSES,
                        from,
                        to
                )) {
            throw new ChatRequestException(GENERATION_IN_PROGRESS);
        }

        SolutionBundleEntity bundle = bundleRepository.findByStoreIdAndTargetDate(storeId, today)
                .filter(candidate -> candidate.getStatus() == SolutionBundleStatus.COMPLETED)
                .orElseThrow(ChatInsufficientDataException::new);
        List<SolutionEntity> cards = solutionRepository
                .findAllBySolutionBundleIdOrderByRankNoAsc(bundle.getId());
        if (cards.isEmpty()) {
            throw new ChatRequestException(SOLUTION_NOT_READY);
        }

        List<ChatAiRequest.History> history = chatMessageRepository
                .findAllByUserIdAndStatusOrderByCreatedAtAscIdAsc(userId, ChatMessageStatus.COMPLETED)
                .stream()
                .map(message -> new ChatAiRequest.History(
                        message.getRole().name(),
                        message.getContent()
                ))
                .toList();

        LocalDateTime now = LocalDateTime.now(clock);
        if (request.retryOfMessageId() == null) {
            chatMessageRepository.save(ChatMessageEntity.user(userId, bundle.getId(), question, now));
        } else {
            ChatMessageEntity retryTarget = chatMessageRepository
                    .findByIdAndUserIdAndRoleAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                            request.retryOfMessageId(),
                            userId,
                            ChatMessageRole.USER,
                            from,
                            to
                    )
                    .orElseThrow(() -> new ChatRequestException(RETRY_TARGET_NOT_FOUND));
            question = retryTarget.getContent();
        }
        ChatMessageEntity assistant = chatMessageRepository.save(
                ChatMessageEntity.assistant(userId, bundle.getId(), now)
        );

        return new ChatStreamPlan(
                assistant.getId(),
                new ChatAiRequest(
                        userId,
                        storeId,
                        today,
                        question,
                        history,
                        cards.stream().map(this::toContextCard).toList()
                )
        );
    }

    public void stream(ChatStreamPlan plan, OutputStream outputStream) {
        StringBuilder answer = new StringBuilder();
        AtomicReference<String> evidenceJson = new AtomicReference<>();
        AtomicBoolean started = new AtomicBoolean();
        AtomicBoolean failed = new AtomicBoolean();
        try {
            aiClient.stream(plan.aiRequest(), event -> {
                if (event.type() == ChatAiEvent.Type.ERROR) {
                    failed.set(true);
                    persistenceService.fail(plan.assistantMessageId());
                    writeEvent(outputStream, Map.of(
                            "event", "error",
                            "data", Map.of(
                                    "messageId", plan.assistantMessageId(),
                                    "code", event.errorCode(),
                                    "message", event.errorMessage()
                            )
                    ));
                    return;
                }
                if (event.content() == null || event.content().isEmpty() || failed.get()) {
                    return;
                }
                if (started.compareAndSet(false, true)) {
                    persistenceService.startStreaming(plan.assistantMessageId());
                }
                answer.append(event.content());
                if (event.evidenceJson() != null) {
                    evidenceJson.set(event.evidenceJson());
                }
                writeChunk(outputStream, plan.assistantMessageId(), event);
            });
            if (!failed.get()) {
                if (answer.isEmpty()) {
                    throw new ChatAiException("AI 챗봇이 빈 답변을 반환했습니다.");
                }
                persistenceService.complete(
                        plan.assistantMessageId(),
                        answer.toString(),
                        evidenceJson.get()
                );
            }
        } catch (RuntimeException exception) {
            if (!failed.get()) {
                persistenceService.fail(plan.assistantMessageId());
                writeEvent(outputStream, Map.of(
                        "event", "error",
                        "data", Map.of(
                                "messageId", plan.assistantMessageId(),
                                "code", "AI_GENERATION_ERROR",
                                "message", "답변 생성에 실패했습니다."
                        )
                ));
            }
        } finally {
            writeRaw(outputStream, "data: [DONE]\n\n");
        }
    }

    private void writeChunk(OutputStream outputStream, Long messageId, ChatAiEvent event) {
        Object evidence = event.evidenceJson() == null
                ? null
                : objectMapper.readTree(event.evidenceJson());
        writeEvent(outputStream, Map.of(
                "event", "answerChunk",
                "data", new ChatChunkPayload(messageId, event.content(), evidence)
        ));
    }

    private void writeEvent(OutputStream outputStream, Object body) {
        writeRaw(outputStream, "data: " + objectMapper.writeValueAsString(body) + "\n\n");
    }

    private void writeRaw(OutputStream outputStream, String value) {
        try {
            outputStream.write(value.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private ChatAiRequest.ContextCard toContextCard(SolutionEntity card) {
        return new ChatAiRequest.ContextCard(
                card.getRankNo(),
                card.getTitle(),
                card.getSummaryText(),
                card.getDetailText(),
                card.getEvidenceText()
        );
    }

    private ChatHistoryResponse.Message toResponse(ChatMessageEntity message) {
        return new ChatHistoryResponse.Message(
                message.getId(),
                message.getRole().name(),
                message.getContent(),
                message.getStatus().name(),
                OffsetDateTime.of(message.getCreatedAt(), clock.getZone().getRules()
                        .getOffset(message.getCreatedAt()))
        );
    }

    private String validateContent(String content) {
        if (content == null) {
            throw new ChatRequestException(INVALID_CONTENT);
        }
        long nonWhitespaceLength = content.codePoints()
                .filter(codePoint -> !Character.isWhitespace(codePoint))
                .count();
        if (nonWhitespaceLength < 1 || nonWhitespaceLength > 300) {
            throw new ChatRequestException(INVALID_CONTENT);
        }
        return content.trim();
    }

    private LocalDateTime startOf(LocalDate date) {
        return date.atStartOfDay();
    }

    private void requireOwnership(Long userId, Long storeId) {
        if (!ownershipRepository.existsActiveStoreOwnedBy(storeId, userId)) {
            throw new ChatRequestException(STORE_OWNER_REQUIRED);
        }
    }

    private record ChatChunkPayload(
            Long messageId,
            String content,
            Object evidence
    ) {
    }
}
