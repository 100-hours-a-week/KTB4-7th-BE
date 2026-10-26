package com.memme.service.noti;

import com.memme.dto.noti.NotificationSseEvent;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class NotificationSseConnectionService {

    private final ConcurrentMap<Long, SseEmitter> connections = new ConcurrentHashMap<>();
    private final Supplier<SseEmitter> emitterFactory;

    public NotificationSseConnectionService() {
        this(SseEmitter::new);
    }

    NotificationSseConnectionService(Supplier<SseEmitter> emitterFactory) {
        this.emitterFactory = Objects.requireNonNull(emitterFactory, "emitterFactory");
    }

    public SseEmitter connect(Long userId) {
        Objects.requireNonNull(userId, "userId");

        SseEmitter emitter = emitterFactory.get();
        emitter.onCompletion(() -> removeIfCurrent(userId, emitter));
        emitter.onTimeout(() -> removeIfCurrent(userId, emitter));
        emitter.onError(error -> removeIfCurrent(userId, emitter));

        SseEmitter previous = connections.put(userId, emitter);
        if (previous != null) {
            previous.complete();
        }
        return emitter;
    }

    public void sendToUser(Long userId, NotificationSseEvent event) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(event, "event");

        SseEmitter emitter = connections.get(userId);
        if (emitter == null) {
            return;
        }

        try {
            emitter.send(SseEmitter.event().name(event.name()).data(event.data()));
        } catch (IOException | IllegalStateException exception) {
            removeIfCurrent(userId, emitter);
        }
    }

    private void removeIfCurrent(Long userId, SseEmitter emitter) {
        connections.remove(userId, emitter);
    }
}
