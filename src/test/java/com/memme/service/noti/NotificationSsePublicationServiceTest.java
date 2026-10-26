package com.memme.service.noti;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.memme.entity.noti.NotificationType;
import com.memme.repository.noti.NotificationSseQueryRepository.NotificationIdentity;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class NotificationSsePublicationServiceTest {

    private final NotificationSseDeliveryService deliveryService = mock(NotificationSseDeliveryService.class);
    private final NotificationSsePublicationService service = new NotificationSsePublicationService(deliveryService);
    private final List<NotificationIdentity> identities = List.of(new NotificationIdentity(1L, "key-1"));

    @AfterEach
    void clearTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void 커밋된_경우에만_저장된_알림의_발행을_요청한다() {
        beginTransaction();

        service.publishAfterCommit(NotificationType.SOLUTION_READY, identities);

        verify(deliveryService, never()).deliver(NotificationType.SOLUTION_READY, identities);
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
        TransactionSynchronizationManager.getSynchronizations().getFirst().afterCommit();

        verify(deliveryService).deliver(NotificationType.SOLUTION_READY, identities);
    }

    @Test
    void 롤백된_경우에는_알림을_발행하지_않는다() {
        beginTransaction();

        service.publishAfterCommit(NotificationType.SOLUTION_READY, identities);
        TransactionSynchronizationManager.getSynchronizations().getFirst()
                .afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(deliveryService, never()).deliver(NotificationType.SOLUTION_READY, identities);
    }

    @Test
    void 전송에_실패해도_이미_완료된_커밋의_호출자는_실패시키지_않는다() {
        beginTransaction();
        doThrow(new IllegalStateException("SSE transport error"))
                .when(deliveryService).deliver(NotificationType.SOLUTION_READY, identities);

        service.publishAfterCommit(NotificationType.SOLUTION_READY, identities);

        assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations().getFirst().afterCommit())
                .doesNotThrowAnyException();
    }

    private void beginTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }
}
