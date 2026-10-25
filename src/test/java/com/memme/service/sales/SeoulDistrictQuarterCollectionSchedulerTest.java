package com.memme.service.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.memme.entity.sales.SeoulDistrictQuarterImportEntity;
import com.memme.repository.sales.SeoulDistrictQuarterImportRepository;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.scheduling.annotation.Scheduled;

@ExtendWith(OutputCaptureExtension.class)
class SeoulDistrictQuarterCollectionSchedulerTest {

    private static final Clock OCTOBER = Clock.fixed(Instant.parse("2026-10-20T03:00:00Z"), ZoneId.of("UTC"));
    private final SeoulDistrictQuarterCollectionService collection = mock(SeoulDistrictQuarterCollectionService.class);
    private final SeoulDistrictQuarterImportRepository imports = mock(SeoulDistrictQuarterImportRepository.class);

    @Test
    void 가장_최근_완결_분기가_미발표이면_이전_유효_분기를_찾는다() {
        when(collection.collect("2026Q3")).thenThrow(new IllegalStateException("unpublished"));
        when(collection.collect("2026Q2")).thenReturn(true);

        scheduler(OCTOBER).collectOnStartup();

        var ordered = inOrder(collection);
        ordered.verify(collection).collect("2026Q3");
        ordered.verify(collection).collect("2026Q2");
        ordered.verifyNoMoreInteractions();
    }

    @Test
    void 수집_실패_로그에_예외_메시지와_스택_트레이스를_남긴다(CapturedOutput output) {
        when(collection.collect("2026Q3")).thenAnswer(ignored -> {
            throw new IllegalStateException("Seoul data unavailable");
        });
        when(collection.collect("2026Q2")).thenReturn(true);

        scheduler(OCTOBER).collectOnStartup();

        assertThat(output.getOut())
                .contains("Seoul district quarter collection failed: trigger=startup, quarter=2026Q3, "
                                + "cause=IllegalStateException: Seoul data unavailable",
                        "at com.memme.service.sales.SeoulDistrictQuarterCollectionScheduler.collectLatest");
    }

    @Test
    void 최신_완료_분기가_있으면_다시_수집하지_않는다() {
        SeoulDistrictQuarterImportEntity completed = SeoulDistrictQuarterImportEntity.start(
                "2026Q3", "CS100010", LocalDateTime.of(2026, 10, 1, 0, 0));
        completed.complete(LocalDateTime.of(2026, 10, 20, 0, 0));
        when(imports.findByBasisQuarterAndIndustryCode("2026Q3", "CS100010"))
                .thenReturn(Optional.of(completed));

        scheduler(OCTOBER).collectDaily();

        verify(collection, never()).collect("2026Q3");
        verify(collection, never()).collect("2026Q2");
    }

    @Test
    void 실패한_분기는_정기_실행에서_재시도한다() {
        SeoulDistrictQuarterImportEntity failed = SeoulDistrictQuarterImportEntity.start(
                "2026Q3", "CS100010", LocalDateTime.of(2026, 10, 1, 0, 0));
        failed.fail(LocalDateTime.of(2026, 10, 2, 0, 0));
        when(imports.findByBasisQuarterAndIndustryCode("2026Q3", "CS100010"))
                .thenReturn(Optional.of(failed));
        when(collection.collect("2026Q3")).thenReturn(true);

        scheduler(OCTOBER).collectDaily();

        verify(collection).collect("2026Q3");
        verify(collection, never()).collect("2026Q2");
    }

    @Test
    void 분기_첫날에는_직전_완결_분기를_확인한다() {
        Clock aprilFirst = Clock.fixed(Instant.parse("2026-03-31T15:00:00Z"), ZoneId.of("UTC"));
        when(collection.collect("2026Q1")).thenReturn(true);

        scheduler(aprilFirst).collectDaily();

        verify(collection).collect("2026Q1");
        verify(collection, never()).collect("2026Q2");
    }

    @Test
    void 최초_수집_중에는_정기_수집을_중복_실행하지_않는다() throws InterruptedException {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(collection.collect("2026Q3")).thenAnswer(ignored -> {
            entered.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return true;
        });
        SeoulDistrictQuarterCollectionScheduler scheduler = scheduler(OCTOBER);

        Thread first = new Thread(scheduler::collectOnStartup);
        first.start();
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        scheduler.collectDaily();
        release.countDown();
        first.join(5000);

        assertThat(first.isAlive()).isFalse();
        verify(collection).collect("2026Q3");
    }

    @Test
    void 실패한_실행_뒤에는_다음_실행을_허용한다() {
        when(imports.findByBasisQuarterAndIndustryCode("2026Q3", "CS100010"))
                .thenThrow(new IllegalStateException("db unavailable"))
                .thenReturn(Optional.empty());
        when(collection.collect("2026Q3")).thenReturn(true);
        SeoulDistrictQuarterCollectionScheduler scheduler = scheduler(OCTOBER);

        scheduler.collectOnStartup();
        scheduler.collectDaily();

        verify(imports, times(2)).findByBasisQuarterAndIndustryCode("2026Q3", "CS100010");
        verify(collection).collect("2026Q3");
    }

    @Test
    void 배치_태스크에서만_명시적으로_활성화한다() {
        ApplicationContextRunner context = new ApplicationContextRunner()
                .withBean(SeoulDistrictQuarterCollectionService.class, () -> collection)
                .withBean(SeoulDistrictQuarterImportRepository.class, () -> imports)
                .withBean(Clock.class, () -> OCTOBER)
                .withUserConfiguration(SeoulDistrictQuarterCollectionScheduler.class);

        context.run(result -> assertThat(result).doesNotHaveBean(SeoulDistrictQuarterCollectionScheduler.class));
        context.withPropertyValues("app.seoul-district-import.enabled=true")
                .run(result -> assertThat(result).hasSingleBean(SeoulDistrictQuarterCollectionScheduler.class));
    }

    @Test
    void 정기_수집은_KST_매일_정오에_예약된다() throws NoSuchMethodException {
        Method method = SeoulDistrictQuarterCollectionScheduler.class.getMethod("collectDaily");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo("${SEOUL_DISTRICT_IMPORT_CRON:0 0 12 * * *}");
        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
    }

    private SeoulDistrictQuarterCollectionScheduler scheduler(Clock clock) {
        return new SeoulDistrictQuarterCollectionScheduler(collection, imports, clock);
    }
}
