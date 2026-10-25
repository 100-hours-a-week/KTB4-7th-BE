package com.memme.service.noti.batch;

import com.memme.repository.noti.NotificationBulkInsertRepository;
import java.time.LocalDate;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.ChunkOrientedStepBuilder;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.EnableJdbcJobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@EnableBatchProcessing
@EnableJdbcJobRepository
public class SolutionReadyNotificationBatchConfiguration {

    static final int CHUNK_SIZE = 500;

    @Bean
    @StepScope
    SolutionReadyNotificationItemReader solutionReadyNotificationItemReader(
            com.memme.repository.solution.SolutionBundleRepository solutionBundleRepository,
            com.memme.repository.store.StoreRepository storeRepository,
            com.memme.repository.noti.NotificationPreferenceRepository preferenceRepository,
            com.memme.repository.noti.NotificationRepository notificationRepository,
            @Value("#{jobParameters['targetDate']}") LocalDate targetDate
    ) {
        return new SolutionReadyNotificationItemReader(
                solutionBundleRepository,
                storeRepository,
                preferenceRepository,
                notificationRepository,
                targetDate
        );
    }

    @Bean
    Step solutionReadyNotificationStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ItemStreamReader<SolutionReadyNotificationCandidate> solutionReadyNotificationItemReader,
            SolutionReadyNotificationItemProcessor processor,
            SolutionReadyNotificationItemWriter writer
    ) {
        return new ChunkOrientedStepBuilder<SolutionReadyNotificationCandidate,
                NotificationBulkInsertRepository.NotificationRow>(
                "solutionReadyNotificationStep", jobRepository, CHUNK_SIZE)
                .transactionManager(transactionManager)
                .reader(solutionReadyNotificationItemReader)
                .processor(processor)
                .writer(writer)
                .build();
    }

    @Bean
    Job solutionReadyNotificationJob(JobRepository jobRepository, Step solutionReadyNotificationStep) {
        return new JobBuilder("solutionReadyNotificationJob", jobRepository)
                .start(solutionReadyNotificationStep)
                .build();
    }
}
