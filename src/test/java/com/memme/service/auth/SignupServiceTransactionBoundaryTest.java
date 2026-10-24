package com.memme.service.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.memme.dto.auth.SignupAccountRequest;
import com.memme.repository.auth.SignupDraftRepository;
import com.memme.repository.auth.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = SignupServiceTransactionBoundaryTest.TestConfig.class)
class SignupServiceTransactionBoundaryTest {

    @Autowired
    private SignupService signupService;

    @Autowired
    private TransactionProbe transactionProbe;

    @Test
    void 비밀번호_해시는_트랜잭션_밖에서_저장은_짧은_트랜잭션_안에서_실행한다() {
        SignupAccountRequest request = new SignupAccountRequest(
                "owner@memme.com",
                "Password1!",
                "Password1!",
                "01012345678",
                new SignupAccountRequest.Agreements(true, "2026-09", true, "2026-09")
        );

        var response = signupService.signupAccount(request);

        assertNotNull(response.signupToken());
        assertFalse(transactionProbe.isPasswordEncodingInTransaction());
        assertTrue(transactionProbe.isDraftSavingInTransaction());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        PlatformTransactionManager transactionManager() {
            return new TrackingTransactionManager();
        }

        @Bean
        TransactionProbe transactionProbe() {
            return new TransactionProbe();
        }

        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }

        @Bean
        SignupDraftRepository signupDraftRepository(TransactionProbe transactionProbe) {
            SignupDraftRepository repository = mock(SignupDraftRepository.class);
            when(repository.save(any())).thenAnswer(invocation -> {
                transactionProbe.setDraftSavingInTransaction(
                        TransactionSynchronizationManager.isActualTransactionActive()
                );
                return invocation.getArgument(0);
            });
            return repository;
        }

        @Bean
        PasswordEncoder passwordEncoder(TransactionProbe transactionProbe) {
            return new PasswordEncoder() {
                @Override
                public String encode(CharSequence rawPassword) {
                    transactionProbe.setPasswordEncodingInTransaction(
                            TransactionSynchronizationManager.isActualTransactionActive()
                    );
                    return "encoded-password";
                }

                @Override
                public boolean matches(CharSequence rawPassword, String encodedPassword) {
                    return false;
                }
            };
        }

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-09-17T01:00:00Z"), ZoneId.of("Asia/Seoul"));
        }

        @Bean
        SignupService signupService(
                UserRepository userRepository,
                SignupDraftRepository signupDraftRepository,
                PasswordEncoder passwordEncoder,
                Clock clock,
                PlatformTransactionManager transactionManager
        ) {
            return new SignupService(userRepository, signupDraftRepository, passwordEncoder, clock, transactionManager);
        }
    }

    static class TransactionProbe {

        private boolean passwordEncodingInTransaction;
        private boolean draftSavingInTransaction;

        boolean isPasswordEncodingInTransaction() {
            return passwordEncodingInTransaction;
        }

        void setPasswordEncodingInTransaction(boolean passwordEncodingInTransaction) {
            this.passwordEncodingInTransaction = passwordEncodingInTransaction;
        }

        boolean isDraftSavingInTransaction() {
            return draftSavingInTransaction;
        }

        void setDraftSavingInTransaction(boolean draftSavingInTransaction) {
            this.draftSavingInTransaction = draftSavingInTransaction;
        }
    }

    static class TrackingTransactionManager implements PlatformTransactionManager {

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            boolean alreadyActive = TransactionSynchronizationManager.isActualTransactionActive();
            if (!alreadyActive) {
                TransactionSynchronizationManager.setActualTransactionActive(true);
            }
            return new TrackingTransactionStatus(!alreadyActive, alreadyActive);
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
            if (status instanceof TrackingTransactionStatus trackingStatus && trackingStatus.isNewTransaction()) {
                TransactionSynchronizationManager.setActualTransactionActive(trackingStatus.wasAlreadyActive());
            }
        }
    }

    static class TrackingTransactionStatus extends SimpleTransactionStatus {

        private final boolean alreadyActive;

        TrackingTransactionStatus(boolean newTransaction, boolean alreadyActive) {
            super(newTransaction);
            this.alreadyActive = alreadyActive;
        }

        boolean wasAlreadyActive() {
            return alreadyActive;
        }
    }
}
