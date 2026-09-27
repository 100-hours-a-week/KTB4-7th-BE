package com.memme.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.memme.repository.auth.UserRepository;
import com.memme.repository.noti.NotificationPreferenceRepository;
import com.memme.repository.store.StoreBusinessHoursRepository;
import com.memme.repository.store.StoreRepository;
import com.memme.service.auth.QaAccountSeeder;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

class QaAccountSeederConditionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void 기본_설정에서는_QA_계정_시더가_등록되지_않는다() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(QaAccountSeeder.class));
    }

    @Test
    void 명시적으로_활성화하면_QA_계정_시더가_등록된다() {
        contextRunner
                .withPropertyValues("app.qa-account.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(QaAccountSeeder.class));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(QaAccountProperties.class)
    @Import(QaAccountSeeder.class)
    static class TestConfiguration {

        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }

        @Bean
        StoreRepository storeRepository() {
            return mock(StoreRepository.class);
        }

        @Bean
        StoreBusinessHoursRepository storeBusinessHoursRepository() {
            return mock(StoreBusinessHoursRepository.class);
        }

        @Bean
        NotificationPreferenceRepository notificationPreferenceRepository() {
            return mock(NotificationPreferenceRepository.class);
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return mock(PasswordEncoder.class);
        }

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }
}
