package com.memme.service.noti;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.memme.entity.auth.User;
import com.memme.entity.noti.FcmDelivery;
import com.memme.entity.noti.FcmToken;
import com.memme.entity.noti.Notification;
import com.memme.entity.noti.NotificationType;
import com.memme.service.noti.fcm.FcmClient;
import com.memme.service.noti.fcm.FcmSendResult;
import com.memme.repository.noti.FcmDeliveryRepository;
import com.memme.repository.noti.FcmTokenRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FcmDeliveryAttemptServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 29, 8, 0);
    private final FcmDeliveryRepository deliveryRepository = mock(FcmDeliveryRepository.class);
    private final FcmTokenRepository tokenRepository = mock(FcmTokenRepository.class);
    private final FcmClient fcmClient = mock(FcmClient.class);

    @Test
    void 발송_성공을_기록하고_다시_발송하지_않는다() {
        FcmDelivery delivery = delivery();
        when(deliveryRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(delivery));
        when(fcmClient.send(any())).thenReturn(FcmSendResult.sent("message-1"));

        serviceAt(START).attempt(1L);
        serviceAt(START.plusMinutes(10)).attempt(1L);

        assertThat(delivery.getStatus()).isEqualTo(FcmDelivery.Status.SENT);
        assertThat(delivery.getAttemptCount()).isEqualTo(1);
        assertThat(delivery.getSentAt()).isEqualTo(START);
        verify(fcmClient).send(any());
    }

    @Test
    void 재시도_가능한_실패는_1분과_5분_뒤에만_재시도한다() {
        FcmDelivery delivery = delivery();
        when(deliveryRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(delivery));
        when(fcmClient.send(any())).thenReturn(FcmSendResult.retryableFailure("UNAVAILABLE"));

        serviceAt(START).attempt(1L);
        serviceAt(START.plusSeconds(59)).attempt(1L);
        assertThat(delivery.getAttemptCount()).isEqualTo(1);
        serviceAt(START.plusMinutes(1)).attempt(1L);
        serviceAt(START.plusMinutes(5)).attempt(1L);
        assertThat(delivery.getAttemptCount()).isEqualTo(2);
        serviceAt(START.plusMinutes(6)).attempt(1L);
        serviceAt(START.plusMinutes(12)).attempt(1L);

        assertThat(delivery.getStatus()).isEqualTo(FcmDelivery.Status.FAILED);
        assertThat(delivery.getAttemptCount()).isEqualTo(3);
    }

    @Test
    void 무효_토큰은_삭제하고_재시도하지_않는다() {
        FcmDelivery delivery = delivery();
        FcmToken token = delivery.getFcmToken();
        when(deliveryRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(delivery));
        when(fcmClient.send(any())).thenReturn(FcmSendResult.invalidToken("UNREGISTERED"));

        serviceAt(START).attempt(1L);
        serviceAt(START.plusMinutes(10)).attempt(1L);

        assertThat(delivery.getStatus()).isEqualTo(FcmDelivery.Status.FAILED);
        assertThat(delivery.getLastErrorCode()).isEqualTo("UNREGISTERED");
        assertThat(delivery.getAttemptCount()).isEqualTo(1);
        verify(tokenRepository).delete(token);
        verify(fcmClient).send(any());
    }

    @Test
    void 영구_실패는_재시도하지_않는다() {
        FcmDelivery delivery = delivery();
        when(deliveryRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(delivery));
        when(fcmClient.send(any())).thenReturn(FcmSendResult.permanentFailure("INVALID_ARGUMENT"));

        serviceAt(START).attempt(1L);
        serviceAt(START.plusMinutes(10)).attempt(1L);

        assertThat(delivery.getAttemptCount()).isEqualTo(1);
        verify(tokenRepository, never()).delete(any());
        verify(fcmClient).send(any());
    }

    private FcmDeliveryAttemptService serviceAt(LocalDateTime now) {
        Clock clock = Clock.fixed(now.atZone(ZONE).toInstant(), ZONE);
        return new FcmDeliveryAttemptService(deliveryRepository, tokenRepository, fcmClient, clock);
    }

    private FcmDelivery delivery() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(7L);
        Notification notification = Notification.create(user, NotificationType.SOLUTION_READY,
                "solution-ready:10", "제목", "내용", "SOLUTION_BUNDLE", 10L, START, START);
        org.springframework.test.util.ReflectionTestUtils.setField(notification, "id", 9L);
        FcmToken token = FcmToken.register(user, "raw-token", "hash", START);
        FcmDelivery delivery = FcmDelivery.pending(notification, token, "hash", START);
        org.springframework.test.util.ReflectionTestUtils.setField(delivery, "id", 1L);
        return delivery;
    }
}
