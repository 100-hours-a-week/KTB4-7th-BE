package com.memme.service.sales.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SalesStorageConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    LocalSalesFileStorage.class,
                    S3SalesStorageConfig.class,
                    S3SalesFileStorage.class
            );

    @Test
    void usesLocalStorageByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(SalesFileStorage.class);
            assertThat(context).hasSingleBean(LocalSalesFileStorage.class);
            assertThat(context).doesNotHaveBean(S3SalesFileStorage.class);
        });
    }

    @Test
    void usesS3StorageWhenConfigured() {
        contextRunner
                .withPropertyValues(
                        "app.sales.storage.type=s3",
                        "app.sales.storage.s3.bucket=private-sales",
                        "app.sales.storage.s3.region=ap-northeast-2"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(SalesFileStorage.class);
                    assertThat(context).hasSingleBean(S3SalesFileStorage.class);
                    assertThat(context).doesNotHaveBean(LocalSalesFileStorage.class);
                });
    }
}
