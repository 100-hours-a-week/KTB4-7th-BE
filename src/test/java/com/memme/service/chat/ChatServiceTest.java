package com.memme.service.chat;

import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import com.memme.dto.chat.ChatHistoryResponse;
import com.memme.dto.chat.ChatMessageRequest;
import com.memme.entity.chat.ChatMessageEntity;
import com.memme.entity.chat.ChatMessageStatus;
import com.memme.entity.solution.SolutionBundleEntity;
import com.memme.entity.solution.SolutionBundleStatus;
import com.memme.entity.solution.SolutionEntity;
import com.memme.exception.chat.ChatInsufficientDataException;
import com.memme.exception.chat.ChatRequestException;
import com.memme.repository.chat.ChatMessageRepository;
import com.memme.repository.solution.SolutionBundleRepository;
import com.memme.repository.solution.SolutionRepository;
import com.memme.repository.store.StoreOwnershipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-26T01:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private SolutionBundleRepository bundleRepository;
    @Mock
    private SolutionRepository solutionRepository;
    @Mock
    private StoreOwnershipRepository ownershipRepository;
    @Mock
    private ChatAiClient aiClient;
    @Mock
    private ChatMessagePersistenceService persistenceService;

    private ChatService service;

    @BeforeEach
    void setUp() {
        service = new ChatService(
                chatMessageRepository,
                bundleRepository,
                solutionRepository,
                ownershipRepository,
                aiClient,
                persistenceService,
                JsonMapper.builder().build(),
                CLOCK
        );
        lenient().when(ownershipRepository.existsActiveStoreOwnedBy(2L, 1L)).thenReturn(true);
    }

    @Test
    void 오늘_대화가_없으면_안내와_추천_질문을_반환한다() {
        when(chatMessageRepository
                .findAllByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAscIdAsc(
                        anyLong(), any(), any()
                )).thenReturn(List.of());

        ChatHistoryResponse response = service.getToday(1L, 2L);

        assertThat(response.message()).isEqualTo("오늘 대화가 없습니다.");
        assertThat(response.data().chatDate()).isEqualTo(LocalDate.of(2026, 9, 26));
        assertThat(response.data().recommendedQuestions()).hasSize(2);
    }

    @Test
    void 공백을_제외한_질문이_없으면_거절한다() {
        assertThatThrownBy(() -> service.prepare(
                1L,
                2L,
                new ChatMessageRequest("   \n", null)
        )).isInstanceOf(ChatRequestException.class)
                .extracting("reason")
                .isEqualTo(ChatRequestException.Reason.INVALID_CONTENT);
    }

    @Test
    void 오늘_완료된_솔루션이_없으면_AI를_준비하지_않는다() {
        when(bundleRepository.findByStoreIdAndTargetDate(2L, LocalDate.of(2026, 9, 26)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.prepare(
                1L,
                2L,
                new ChatMessageRequest("오늘 뭘 해야 해?", null)
        )).isInstanceOf(ChatInsufficientDataException.class);
    }

    @Test
    void 질문과_답변_저장을_준비하고_AI_문맥을_만든다() {
        SolutionBundleEntity bundle = mock(SolutionBundleEntity.class);
        when(bundle.getId()).thenReturn(10L);
        when(bundle.getStatus()).thenReturn(SolutionBundleStatus.COMPLETED);
        when(bundleRepository.findByStoreIdAndTargetDate(2L, LocalDate.of(2026, 9, 26)))
                .thenReturn(Optional.of(bundle));
        SolutionEntity solution = mock(SolutionEntity.class);
        when(solution.getRankNo()).thenReturn(1);
        when(solution.getTitle()).thenReturn("오후 재고 점검");
        when(solution.getSummaryText()).thenReturn("인기 메뉴를 확인하세요.");
        when(solution.getDetailText()).thenReturn("14시 이전에 재고를 확인하세요.");
        when(solutionRepository.findAllBySolutionBundleIdOrderByRankNoAsc(10L))
                .thenReturn(List.of(solution));
        when(chatMessageRepository.findAllByUserIdAndStatusOrderByCreatedAtAscIdAsc(
                1L, ChatMessageStatus.COMPLETED
        )).thenReturn(List.of());
        ChatMessageEntity savedAssistant = mock(ChatMessageEntity.class);
        when(savedAssistant.getId()).thenReturn(12L);
        when(chatMessageRepository.save(any(ChatMessageEntity.class)))
                .thenAnswer(invocation -> {
                    ChatMessageEntity message = invocation.getArgument(0);
                    return message.getRole().name().equals("ASSISTANT") ? savedAssistant : message;
                });

        ChatStreamPlan plan = service.prepare(
                1L,
                2L,
                new ChatMessageRequest("오늘 뭘 해야 해?", null)
        );

        assertThat(plan.assistantMessageId()).isEqualTo(12L);
        assertThat(plan.aiRequest().question()).isEqualTo("오늘 뭘 해야 해?");
        assertThat(plan.aiRequest().context()).hasSize(1);
    }

    @Test
    void AI_청크를_SSE로_전달하고_완성_답변을_저장한다() {
        ChatStreamPlan plan = new ChatStreamPlan(12L, mock(com.memme.dto.chat.ChatAiRequest.class));
        org.mockito.Mockito.doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            java.util.function.Consumer<ChatAiEvent> consumer = invocation.getArgument(1);
            consumer.accept(ChatAiEvent.chunk("오후 ", null));
            consumer.accept(ChatAiEvent.chunk("재고를 확인하세요.", "{\"metric\":\"sales_summary\"}"));
            return null;
        }).when(aiClient).stream(any(), any());
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        service.stream(plan, output);

        String body = output.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).contains("answerChunk", "오후", "[DONE]");
        verify(persistenceService).startStreaming(12L);
        verify(persistenceService).complete(
                12L,
                "오후 재고를 확인하세요.",
                "{\"metric\":\"sales_summary\"}"
        );
    }

}
