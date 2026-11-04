package com.memme.service.noti;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class NotificationSchedulerRoleTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SchedulerConfiguration.class, Dependencies.class);

    @Test
    void 웹_태스크에서는_알림_스케줄러를_등록하지_않는다() {
        contextRunner
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("notification-web"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(SolutionReadyNotificationScheduler.class);
                    assertThat(context).doesNotHaveBean(SalesUploadReminderScheduler.class);
                    assertThat(context).doesNotHaveBean(RankingChangeNotificationScheduler.class);
                });
    }

    @Test
    void 배치_태스크에서는_알림_스케줄러_세_개를_등록한다() {
        contextRunner
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("notification-batch"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(SolutionReadyNotificationScheduler.class);
                    assertThat(context).hasSingleBean(SalesUploadReminderScheduler.class);
                    assertThat(context).hasSingleBean(RankingChangeNotificationScheduler.class);
                });
    }

    @Test
    void 로컬에서는_알림_스케줄러_세_개를_등록한다() {
        contextRunner
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("local"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(SolutionReadyNotificationScheduler.class);
                    assertThat(context).hasSingleBean(SalesUploadReminderScheduler.class);
                    assertThat(context).hasSingleBean(RankingChangeNotificationScheduler.class);
                });
    }

    @Test
    void 알림_배치_프로필이_없으면_다른_배치_프로필만으로는_스케줄러를_등록하지_않는다() {
        contextRunner
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("sales-outbox-batch"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(SolutionReadyNotificationScheduler.class);
                    assertThat(context).doesNotHaveBean(SalesUploadReminderScheduler.class);
                    assertThat(context).doesNotHaveBean(RankingChangeNotificationScheduler.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({
            SolutionReadyNotificationScheduler.class,
            SalesUploadReminderScheduler.class,
            RankingChangeNotificationScheduler.class
    })
    static class SchedulerConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    static class Dependencies {

        @Bean
        Clock clock() {
            return Clock.systemDefaultZone();
        }

        @Bean
        JobOperator jobOperator() {
            return mock(JobOperator.class);
        }

        @Bean
        Job solutionReadyNotificationJob() {
            return mock(Job.class);
        }

        @Bean
        SalesUploadReminderService salesUploadReminderService() {
            return mock(SalesUploadReminderService.class);
        }

        @Bean
        RankingChangeNotificationService rankingChangeNotificationService() {
            return mock(RankingChangeNotificationService.class);
        }
    }
}
