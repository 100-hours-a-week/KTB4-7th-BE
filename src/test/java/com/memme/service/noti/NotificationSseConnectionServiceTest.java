package com.memme.service.noti;

import static org.assertj.core.api.Assertions.assertThat;

import com.memme.dto.noti.NotificationListResponse;
import com.memme.dto.noti.NotificationSseEvent;
import com.memme.entity.noti.NotificationType;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class NotificationSseConnectionServiceTest {

    private final Queue<TrackingSseEmitter> emitters = new ArrayDeque<>();
    private NotificationSseConnectionService service;

    @BeforeEach
    void setUp() {
        service = new NotificationSseConnectionService(() -> {
            TrackingSseEmitter emitter = new TrackingSseEmitter();
            emitters.add(emitter);
            return emitter;
        });
    }

    @Test
    void 같은_사용자가_재연결하면_이전_연결을_종료하고_새_연결로_전달한다() {
        TrackingSseEmitter previous = connect(1L);
        TrackingSseEmitter current = connect(1L);

        service.sendToUser(1L, event(10L));

        assertThat(previous.completed).isTrue();
        assertThat(previous.sentEvents).isEmpty();
        assertThat(current.sentEvents).hasSize(1);
    }

    @Test
    void 연결이_완료되면_사용자_연결을_정리한다() {
        TrackingSseEmitter emitter = connect(1L);

        emitter.triggerCompletion();
        service.sendToUser(1L, event(10L));

        assertThat(emitter.sentEvents).isEmpty();
    }

    @Test
    void 이전_연결의_오류는_새_연결을_제거하지_않고_현재_연결의_오류는_정리한다() {
        TrackingSseEmitter previous = connect(1L);
        TrackingSseEmitter current = connect(1L);

        previous.triggerError(new IOException("previous connection failed"));
        service.sendToUser(1L, event(10L));
        current.triggerError(new IOException("current connection failed"));
        service.sendToUser(1L, event(11L));

        assertThat(previous.sentEvents).isEmpty();
        assertThat(current.sentEvents).hasSize(1);
    }

    @Test
    void 전송이_실패하면_연결을_정리한다() {
        TrackingSseEmitter emitter = connect(1L);
        emitter.sendFailure = new IOException("client disconnected");

        service.sendToUser(1L, event(10L));
        emitter.sendFailure = null;
        service.sendToUser(1L, event(11L));

        assertThat(emitter.sendAttempts).hasValue(1);
        assertThat(emitter.completedWithError).isFalse();
    }

    @Test
    void 한_사용자에게_보낸_이벤트는_다른_사용자_연결에_전달하지_않는다() {
        TrackingSseEmitter firstUser = connect(1L);
        TrackingSseEmitter secondUser = connect(2L);
        NotificationSseEvent event = event(10L);

        service.sendToUser(1L, event);

        assertThat(firstUser.sentEvents).hasSize(1);
        assertThat(secondUser.sentEvents).isEmpty();

        List<Object> frameParts = firstUser.sentEvents.getFirst().build().stream()
                .map(SseEmitter.DataWithMediaType::getData)
                .toList();
        assertThat(frameParts).anySatisfy(part -> assertThat(part.toString()).contains("event:notification"));
        assertThat(frameParts).contains(event.data());
    }

    @Test
    void 타임아웃된_연결을_정리한다() {
        TrackingSseEmitter emitter = connect(1L);

        emitter.triggerTimeout();
        service.sendToUser(1L, event(10L));

        assertThat(emitter.sentEvents).isEmpty();
    }

    private TrackingSseEmitter connect(Long userId) {
        return (TrackingSseEmitter) service.connect(userId);
    }

    private NotificationSseEvent event(Long notificationId) {
        return new NotificationSseEvent(new NotificationListResponse.Item(
                notificationId,
                NotificationType.SOLUTION_READY,
                "솔루션이 준비되었습니다.",
                "오늘의 솔루션을 확인해 주세요.",
                "SOLUTION",
                7L,
                OffsetDateTime.parse("2026-10-26T08:00:00+09:00"),
                null
        ));
    }

    private static class TrackingSseEmitter extends SseEmitter {

        private final List<SseEventBuilder> sentEvents = new ArrayList<>();
        private final AtomicInteger sendAttempts = new AtomicInteger();
        private Runnable completionCallback;
        private Runnable timeoutCallback;
        private Consumer<Throwable> errorCallback;
        private IOException sendFailure;
        private boolean completed;
        private boolean completedWithError;

        @Override
        public void onCompletion(Runnable callback) {
            completionCallback = callback;
        }

        @Override
        public void onTimeout(Runnable callback) {
            timeoutCallback = callback;
        }

        @Override
        public void onError(Consumer<Throwable> callback) {
            errorCallback = callback;
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            sendAttempts.incrementAndGet();
            if (sendFailure != null) {
                throw sendFailure;
            }
            sentEvents.add(builder);
        }

        @Override
        public void complete() {
            completed = true;
            if (completionCallback != null) {
                completionCallback.run();
            }
        }

        @Override
        public void completeWithError(Throwable failure) {
            completedWithError = true;
            if (errorCallback != null) {
                errorCallback.accept(failure);
            }
        }

        private void triggerCompletion() {
            completionCallback.run();
        }

        private void triggerTimeout() {
            timeoutCallback.run();
        }

        private void triggerError(Throwable error) {
            errorCallback.accept(error);
        }
    }
}
