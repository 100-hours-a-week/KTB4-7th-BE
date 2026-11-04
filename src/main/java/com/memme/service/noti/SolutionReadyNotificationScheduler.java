package com.memme.service.noti;

import java.time.Clock;
import java.time.LocalDate;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobExecutionException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 솔루션은 업로드 직후 또는 매일 00시 배치로 생성되지만,
 * "오늘의 솔루션이 도착했습니다" 알림은 생성 시점과 무관하게
 * 매일 08시에 한 번만 발송한다.
 */
@Component
@Profile({"notification-batch", "local", "default"})
public class SolutionReadyNotificationScheduler {

    private final JobOperator jobOperator;
    private final Job solutionReadyNotificationJob;
    private final Clock clock;

    public SolutionReadyNotificationScheduler(JobOperator jobOperator, Job solutionReadyNotificationJob, Clock clock) {
        this.jobOperator = jobOperator;
        this.solutionReadyNotificationJob = solutionReadyNotificationJob;
        this.clock = clock;
    }

    @Scheduled(cron = "${SOLUTION_READY_NOTIFICATION_CRON:0 0 8 * * *}", zone = "Asia/Seoul")
    public void notifyTodaysSolutions() {
        LocalDate targetDate = LocalDate.now(clock);
        JobParameters parameters = new JobParametersBuilder()
                .addLocalDate("targetDate", targetDate)
                .toJobParameters();
        try {
            JobExecution execution = jobOperator.start(solutionReadyNotificationJob, parameters);
            if (execution.getStatus() != BatchStatus.COMPLETED) {
                throw new IllegalStateException(
                        "솔루션 도착 알림 배치가 완료되지 않았습니다: " + execution.getStatus());
            }
        } catch (JobInstanceAlreadyCompleteException | JobExecutionAlreadyRunningException ignored) {
            // A duplicate trigger for the same business date must not create another JobInstance.
        } catch (JobExecutionException exception) {
            throw new IllegalStateException("솔루션 도착 알림 배치를 실행하지 못했습니다.", exception);
        }
    }
}
