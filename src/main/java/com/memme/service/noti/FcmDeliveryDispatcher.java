package com.memme.service.noti;

import com.memme.entity.noti.FcmDelivery;
import com.memme.repository.noti.FcmDeliveryRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile({"notification-batch", "local", "default"})
@ConditionalOnProperty(prefix = "app.fcm", name = "enabled", havingValue = "true")
public class FcmDeliveryDispatcher {

    private final FcmDeliveryRepository deliveryRepository;
    private final FcmDeliveryAttemptService attemptService;
    private final Clock clock;

    public FcmDeliveryDispatcher(
            FcmDeliveryRepository deliveryRepository,
            FcmDeliveryAttemptService attemptService,
            Clock clock
    ) {
        this.deliveryRepository = deliveryRepository;
        this.attemptService = attemptService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${FCM_DELIVERY_POLL_INTERVAL_MS:10000}")
    public void dispatch() {
        for (int page = 0; page < 100; page++) {
            LocalDateTime now = LocalDateTime.now(clock);
            List<FcmDelivery> ready = deliveryRepository.findReadyToAttempt(
                    FcmDelivery.Status.PENDING,
                    List.of(FcmDelivery.Status.PENDING, FcmDelivery.Status.FAILED),
                    FcmDeliveryAttemptService.RETRYABLE_ERRORS.stream().toList(),
                    now.minusMinutes(1), now.minusMinutes(5), PageRequest.of(0, 100));
            if (ready.isEmpty()) {
                return;
            }
            ready.forEach(delivery -> attemptService.attempt(delivery.getId()));
        }
    }
}
