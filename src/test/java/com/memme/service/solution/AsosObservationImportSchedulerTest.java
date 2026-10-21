package com.memme.service.solution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
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
                .withBean(SolutionRegenerationService.class,
                        () -> mock(SolutionRegenerationService.class))
                .withUserConfiguration(AsosObservationImportScheduler.class)
                .run(context -> assertThat(context).hasSingleBean(AsosObservationImportScheduler.class));
    }

    @Test
    void 실행_프로필과_관계없이_자동_적재와_스케줄링이_활성화된다() {
        new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("regeneration"))
                .withPropertyValues("app.asos-import.enabled=true")
                .withBean(AsosObservationImportService.class,
                        () -> mock(AsosObservationImportService.class))
                .withBean(SolutionRegenerationService.class,
                        () -> mock(SolutionRegenerationService.class))
                .withUserConfiguration(AsosObservationImportScheduler.class,
                        com.memme.config.AsyncConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(AsosObservationImportScheduler.class);
                    assertThat(context).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class);
                });
    }

    @Test
    void 최초_실행과_정기_실행은_기존_적재_서비스를_호출한다() {
        AsosObservationImportService service = mock(AsosObservationImportService.class);
        SolutionRegenerationService regeneration = mock(SolutionRegenerationService.class);
        when(service.importForActiveStores()).thenReturn(1);
        AsosObservationImportScheduler scheduler = new AsosObservationImportScheduler(service, regeneration);

        scheduler.importOnStartup();
        scheduler.importDaily();

        verify(service, times(2)).importForActiveStores();
        verify(regeneration, times(2)).regenerateTodayAfterObservationImport();
    }

    @Test
    void 신규_관측값이_없으면_솔루션_재생성을_호출하지_않는다() {
        AsosObservationImportService service = mock(AsosObservationImportService.class);
        SolutionRegenerationService regeneration = mock(SolutionRegenerationService.class);
        when(service.importForActiveStores()).thenReturn(0);
        AsosObservationImportScheduler scheduler = new AsosObservationImportScheduler(service, regeneration);

        scheduler.importDaily();

        verify(regeneration, never()).regenerateTodayAfterObservationImport();
    }

    @Test
    void 재생성_실패가_관측값_적재_성공을_실패로_바꾸지_않는다() {
        AsosObservationImportService service = mock(AsosObservationImportService.class);
        SolutionRegenerationService regeneration = mock(SolutionRegenerationService.class);
        when(service.importForActiveStores()).thenReturn(1);
        org.mockito.Mockito.doThrow(new RuntimeException("database unavailable"))
                .when(regeneration).regenerateTodayAfterObservationImport();
        AsosObservationImportScheduler scheduler = new AsosObservationImportScheduler(service, regeneration);

        scheduler.importDaily();

        verify(service).importForActiveStores();
        verify(regeneration).regenerateTodayAfterObservationImport();
    }

    @Test
    void 적재_중에는_중복_실행하지_않는다() throws InterruptedException {
        AsosObservationImportService service = mock(AsosObservationImportService.class);
        SolutionRegenerationService regeneration = mock(SolutionRegenerationService.class);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(service.importForActiveStores()).thenAnswer(invocation -> {
            entered.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return 1;
        });
        AsosObservationImportScheduler scheduler = new AsosObservationImportScheduler(service, regeneration);

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
        SolutionRegenerationService regeneration = mock(SolutionRegenerationService.class);
        when(service.importForActiveStores()).thenThrow(new RuntimeException("provider unavailable"))
                .thenReturn(2);
        AsosObservationImportScheduler scheduler = new AsosObservationImportScheduler(service, regeneration);

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
