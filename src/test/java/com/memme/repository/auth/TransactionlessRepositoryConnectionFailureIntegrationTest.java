package com.memme.repository.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLTransientConnectionException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest(properties = {
        "spring.datasource.hikari.maximum-pool-size=1",
        "spring.datasource.hikari.minimum-idle=1",
        "spring.datasource.hikari.connection-timeout=250"
})
class TransactionlessRepositoryConnectionFailureIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 트랜잭션_없는_파생조회에서_Hikari_연결_획득_시간초과는_DataAccessResourceFailureException으로_번역된다()
            throws Exception {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();

        try (Connection heldConnection = dataSource.getConnection()) {
            assertThat(heldConnection.isClosed()).isFalse();

            assertThatThrownBy(() -> passwordResetTokenRepository.findByTokenHash("a".repeat(64)))
                    .isInstanceOf(DataAccessResourceFailureException.class)
                    .hasRootCauseInstanceOf(SQLTransientConnectionException.class);
        }
    }
}
