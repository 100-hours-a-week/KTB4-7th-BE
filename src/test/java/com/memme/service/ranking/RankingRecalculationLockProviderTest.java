package com.memme.service.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class RankingRecalculationLockProviderTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(LockProviderTestConfiguration.class);

    @Test
    void defaultsToMysqlAndRegistersThePassThroughExecutor() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(RankingRecalculationLockProperties.class);
            assertThat(context.getBean(RankingRecalculationLockProperties.class).getLockProvider())
                    .isEqualTo(RankingRecalculationLockProperties.Provider.MYSQL);
            assertThat(context).hasSingleBean(NoOpRankingRecalculationLockExecutor.class);
        });
    }

    @Test
    void selectingRedisDoesNotRegisterTheMysqlPassThroughExecutor() {
        contextRunner.withPropertyValues("ranking.recalculation.lock-provider=redis")
                .run(context -> {
                    assertThat(context.getBean(RankingRecalculationLockProperties.class).getLockProvider())
                            .isEqualTo(RankingRecalculationLockProperties.Provider.REDIS);
                    assertThat(context).doesNotHaveBean(NoOpRankingRecalculationLockExecutor.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RankingRecalculationLockProperties.class)
    @Import(NoOpRankingRecalculationLockExecutor.class)
    static class LockProviderTestConfiguration {
    }
}
