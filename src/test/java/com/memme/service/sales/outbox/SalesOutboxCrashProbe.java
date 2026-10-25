package com.memme.service.sales.outbox;

import com.memme.entity.sales.SalesUploadOutboxEntity;
import com.memme.repository.sales.SalesUploadRepository;
import com.memme.repository.sales.outbox.SalesOutboxDatabaseClock;
import com.memme.repository.sales.outbox.SalesUploadOutboxRepository;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** 격리된 MySQL 테스트에서만 실행하는 별도 JVM. 정상 종료 훅을 실행하지 않는다. */
public final class SalesOutboxCrashProbe {
    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = SalesUploadOutboxEntity.class)
    @EnableJpaRepositories(basePackageClasses = SalesUploadRepository.class)
    @Import({SalesUploadOutboxCoordinator.class, SalesOutboxRetryPolicy.class, SalesOutboxDatabaseClock.class})
    static class ProbeConfiguration {}

    public static void main(String[] args) {
        String url = System.getenv("OUTBOX_MYSQL_TEST_URL");
        if (url == null || !url.startsWith("jdbc:mysql://127.0.0.1:") || !url.contains("/outbox_test?")) {
            throw new IllegalArgumentException("isolated loopback outbox_test DB required");
        }
        var context = new SpringApplicationBuilder(ProbeConfiguration.class).web(WebApplicationType.NONE).run(
                "--spring.datasource.url=" + url, "--spring.datasource.username=root", "--spring.datasource.password=",
                "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                "--spring.jpa.hibernate.ddl-auto=validate", "--spring.jmx.enabled=false");
        var coordinator = context.getBean(SalesUploadOutboxCoordinator.class);
        var claim = coordinator.claimNext("crash-probe").orElseThrow();
        if (args.length > 0 && args[0].equals("uncommitted-checkpoint")) {
            coordinator.write(claim, job -> {
                job.dataApplied(0, context.getBean(SalesOutboxDatabaseClock.class).now());
                context.getBean(SalesUploadOutboxRepository.class).flush();
                Runtime.getRuntime().halt(74);
                return null;
            });
        }
        Runtime.getRuntime().halt(73);
    }
}
