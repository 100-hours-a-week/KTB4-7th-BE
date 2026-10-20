package com.memme.service.solution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.Scheduled;

class AsosObservationImportSchedulerTest {

    @Test
    void 기본값에서는_웹_태스크에_적재_스케줄러가_생기지_않는다() {
        new ApplicationContextRunner()
                .withBean(AsosObservationImportService.class,
                        () -> mock(AsosObservationImportService.class))
                .withUserConfiguration(AsosObservationImportScheduler.class)
                .run(context -> assertThat(context).doesNotHaveBean(AsosObservationImportScheduler.class));
    }

    @Test
    void 배치_태스크에서_명시적으로_활성화하면_스케줄러가_생긴다() {
        new ApplicationContextRunner()
                .withPropertyValues("app.asos-import.enabled=true")
                .withBean(AsosObservationImportService.class,
                        () -> mock(AsosObservationImportService.class))
                .withUserConfiguration(AsosObservationImportScheduler.class)
                .run(context -> assertThat(context).hasSingleBean(AsosObservationImportScheduler.class));
    }

    @Test
    void 최초_실행과_정기_실행은_기존_적재_서비스를_호출한다() {
        AsosObservationImportService service = mock(AsosObservationImportService.class);
        AsosObservationImportScheduler scheduler = new AsosObservationImportScheduler(service);

        scheduler.importOnStartup();
        scheduler.importDaily();

        verify(service, times(2)).importForActiveStores();
    }

    @Test
    void 적재_중에는_중복_실행하지_않는다() throws InterruptedException {
        AsosObservationImportService service = mock(AsosObservationImportService.class);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(service.importForActiveStores()).thenAnswer(invocation -> {
            entered.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return 1;
        });
        AsosObservationImportScheduler scheduler = new AsosObservationImportScheduler(service);

        Thread first = new Thread(scheduler::importOnStartup);
        first.start();
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        scheduler.importDaily();
        release.countDown();
        first.join(5000);

        assertThat(first.isAlive()).isFalse();
        verify(service).importForActiveStores();
    }

    @Test
    void 실패한_뒤에는_다음_실행을_허용한다() {
        AsosObservationImportService service = mock(AsosObservationImportService.class);
        when(service.importForActiveStores()).thenThrow(new RuntimeException("provider unavailable"))
                .thenReturn(2);
        AsosObservationImportScheduler scheduler = new AsosObservationImportScheduler(service);

        scheduler.importOnStartup();
        scheduler.importDaily();

        verify(service, times(2)).importForActiveStores();
    }

    @Test
    void 정기_적재는_KST_매일_정오에_예약된다() throws NoSuchMethodException {
        Method method = AsosObservationImportScheduler.class.getMethod("importDaily");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo("${ASOS_OBSERVATION_IMPORT_CRON:0 0 12 * * *}");
        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
    }
}
