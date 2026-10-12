package com.memme.service.store.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.memme.service.sales.storage.S3SalesFileStorage;
import com.memme.service.sales.storage.S3SalesStorageConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MenuImageStorageConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(LocalMenuImageStorage.class, MenuImageStorageConfig.class);

    @Test
    void 기본값은_로컬_저장소를_선택한다() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(MenuImageStorage.class);
            assertThat(context).hasSingleBean(LocalMenuImageStorage.class);
            assertThat(context).doesNotHaveBean(S3MenuImageStorage.class);
        });
    }

    @Test
    void 메뉴와_매출이_모두_S3여도_저장소가_각각_선택된다() {
        contextRunner
                .withUserConfiguration(S3SalesStorageConfig.class, S3SalesFileStorage.class)
                .withPropertyValues(
                        "app.menu.storage.type=s3",
                        "app.menu.storage.s3.bucket=private-menu",
                        "app.menu.storage.s3.region=ap-northeast-2",
                        "app.sales.storage.type=s3",
                        "app.sales.storage.s3.bucket=private-sales",
                        "app.sales.storage.s3.region=ap-northeast-2")
                .run(context -> {
                    assertThat(context).hasSingleBean(MenuImageStorage.class);
                    assertThat(context).hasSingleBean(S3MenuImageStorage.class);
                    assertThat(context).doesNotHaveBean(LocalMenuImageStorage.class);
                    assertThat(context).hasSingleBean(S3SalesFileStorage.class);
                });
    }
}
