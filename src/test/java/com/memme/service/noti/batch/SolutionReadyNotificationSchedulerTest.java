package com.memme.service.noti.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.memme.service.noti.SolutionReadyNotificationScheduler;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;

class SolutionReadyNotificationSchedulerTest {

    @Test
    void 매일_한국_날짜를_식별_파라미터로_배치_작업을_실행한다() throws Exception {
        JobOperator jobOperator = mock(JobOperator.class);
        Job job = mock(Job.class);
        JobExecution execution = mock(JobExecution.class);
        when(execution.getStatus()).thenReturn(BatchStatus.COMPLETED);
        when(jobOperator.start(eq(job), any(JobParameters.class))).thenReturn(execution);
        Clock clock = Clock.fixed(Instant.parse("2026-10-23T15:00:00Z"), ZoneId.of("Asia/Seoul"));
        SolutionReadyNotificationScheduler scheduler = new SolutionReadyNotificationScheduler(jobOperator, job, clock);

        scheduler.notifyTodaysSolutions();

        ArgumentCaptor<JobParameters> parameters = ArgumentCaptor.forClass(JobParameters.class);
        verify(jobOperator).start(eq(job), parameters.capture());
        assertThat(parameters.getValue().getLocalDate("targetDate")).isEqualTo(LocalDate.of(2026, 10, 24));
        assertThat(parameters.getValue().getIdentifyingParameters())
                .anySatisfy(parameter -> assertThat(parameter.name()).isEqualTo("targetDate"));
    }
}
