package com.memme.config;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SalesUploadExecutorTest {

    @Test
    void rejectsOverflowWithoutRunningItOnTheRequestThread() throws Exception {
        var executor = new AsyncConfiguration().salesUploadExecutor(1);
        executor.initialize();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch queued = new CountDownLatch(1);
        try {
            executor.execute(() -> {
                started.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            executor.execute(queued::countDown);
            assertThatThrownBy(() -> executor.execute(() -> {
                throw new AssertionError("Rejected task must not run");
            })).isInstanceOf(TaskRejectedException.class);
            assertThat(queued.getCount()).isEqualTo(1);
            release.countDown();
            assertThat(queued.await(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }
}
