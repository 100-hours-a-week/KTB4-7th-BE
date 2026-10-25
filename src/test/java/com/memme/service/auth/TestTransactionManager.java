package com.memme.service.auth;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

final class TestTransactionManager implements PlatformTransactionManager {

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) {
        boolean alreadyActive = TransactionSynchronizationManager.isActualTransactionActive();
        if (definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_NOT_SUPPORTED) {
            if (alreadyActive) {
                TransactionSynchronizationManager.setActualTransactionActive(false);
            }
            return new TestTransactionStatus(false, alreadyActive, true);
        }
        if (!alreadyActive) {
            TransactionSynchronizationManager.setActualTransactionActive(true);
        }
        return new TestTransactionStatus(!alreadyActive, alreadyActive, false);
    }

    @Override
    public void commit(TransactionStatus status) {
        finish(status);
    }

    @Override
    public void rollback(TransactionStatus status) {
        finish(status);
    }

    private void finish(TransactionStatus status) {
        if (status instanceof TestTransactionStatus testStatus) {
            if (testStatus.suspendsExistingTransaction()) {
                TransactionSynchronizationManager.setActualTransactionActive(testStatus.wasAlreadyActive());
            } else if (testStatus.isNewTransaction()) {
                TransactionSynchronizationManager.setActualTransactionActive(testStatus.wasAlreadyActive());
            }
        }
    }

    private static final class TestTransactionStatus extends SimpleTransactionStatus {

        private final boolean alreadyActive;
        private final boolean suspendedExistingTransaction;

        private TestTransactionStatus(
                boolean newTransaction,
                boolean alreadyActive,
                boolean suspendedExistingTransaction
        ) {
            super(newTransaction);
            this.alreadyActive = alreadyActive;
            this.suspendedExistingTransaction = suspendedExistingTransaction;
        }

        private boolean wasAlreadyActive() {
            return alreadyActive;
        }

        private boolean suspendsExistingTransaction() {
            return suspendedExistingTransaction;
        }
    }
}
