package com.memme.config;

import io.sentry.IScopes;
import io.sentry.Sentry;
import io.sentry.SentryOptions;
import io.sentry.spring.jakarta.SentryExceptionResolver;
import io.sentry.spring.jakarta.SentrySpringFilter;
import io.sentry.spring.jakarta.tracing.TransactionNameProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
public class SentryConfiguration {

    @Bean
    SentryInitializer sentryInitializer(
            @Value("${sentry.dsn:}") String dsn,
            @Value("${sentry.environment:local}") String environment
    ) {
        return new SentryInitializer(dsn, environment);
    }

    @Bean
    @DependsOn("sentryInitializer")
    IScopes sentryScopes() {
        return Sentry.getCurrentScopes();
    }

    @Bean
    FilterRegistrationBean<SentrySpringFilter> sentrySpringFilter(IScopes sentryScopes) {
        FilterRegistrationBean<SentrySpringFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new SentrySpringFilter(sentryScopes));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    SentryExceptionResolver sentryExceptionResolver(IScopes sentryScopes) {
        TransactionNameProvider transactionNameProvider = request ->
                request.getMethod() + " " + request.getRequestURI();

        return new SentryExceptionResolver(
                sentryScopes,
                transactionNameProvider,
                Ordered.HIGHEST_PRECEDENCE
        );
    }

    static final class SentryInitializer implements AutoCloseable {

        private final boolean initialized;

        SentryInitializer(String dsn, String environment) {
            initialized = dsn != null && !dsn.isBlank();

            if (initialized) {
                Sentry.init(createOptions(dsn, environment));
            }
        }

        static SentryOptions createOptions(String dsn, String environment) {
            SentryOptions options = new SentryOptions();
            options.setDsn(dsn);
            options.setEnvironment(environment);
            options.setSendDefaultPii(false);
            options.addInAppInclude("com.memme");
            return options;
        }

        @Override
        public void close() {
            if (initialized) {
                Sentry.close();
            }
        }
    }
}
