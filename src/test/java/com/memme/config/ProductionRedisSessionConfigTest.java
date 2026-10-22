package com.memme.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.memme.controller.auth.AuthenticatedUserSession;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.data.redis.RedisSessionRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.CookieSerializer.CookieValue;
import org.springframework.session.web.http.SessionRepositoryFilter;

class ProductionRedisSessionConfigTest {

    private final ProductionRedisSessionConfig config = new ProductionRedisSessionConfig();
    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withUserConfiguration(ProductionRedisSessionConfig.class)
            .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class));

    @Test
    void storesOnlyTheDigestOfTheSessionTokenAndRejectsOtherNamespaces() {
        var serializer = new ProductionRedisSessionConfig.HashedSessionKeySerializer();

        assertThat(new String(serializer.serialize("memme:prod:v2:sessions:abc"), StandardCharsets.UTF_8))
                .isEqualTo("memme:prod:v2:sessions:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThatThrownBy(() -> serializer.serialize("another:sessions:abc"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(serializer.serialize(null)).isNull();
    }

    @Test
    void authenticatedPrincipalSurvivesJdkSerialization() {
        var serializer = new JdkSerializationRedisSerializer();
        var principal = new AuthenticatedUserSession(10L, 20L);

        assertThat(serializer.deserialize(serializer.serialize(principal))).isEqualTo(principal);
    }

    @Test
    void usesTheProductionNamespaceAndThirtyMinuteIdleTimeout() {
        var repository = config.sessionRepository(mock(RedisConnectionFactory.class));
        @SuppressWarnings("unchecked")
        SessionRepository<Session> sessionRepository = (SessionRepository<Session>) (SessionRepository<?>) repository;
        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> operations = (RedisTemplate<String, Object>) repository.getSessionRedisOperations();
        @SuppressWarnings("unchecked")
        RedisSerializer<Object> keySerializer = (RedisSerializer<Object>) (RedisSerializer<?>) operations.getKeySerializer();

        assertThat(sessionRepository.createSession().getMaxInactiveInterval()).isEqualTo(Duration.ofMinutes(30));
        assertThat(new String(keySerializer.serialize("memme:prod:v2:sessions:abc"), StandardCharsets.UTF_8))
                .isEqualTo("memme:prod:v2:sessions:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(operations.getHashValueSerializer()).isInstanceOf(JdkSerializationRedisSerializer.class);
        assertThat(operations.getValueSerializer()).isInstanceOf(JdkSerializationRedisSerializer.class);
    }

    @Test
    void writesAndReadsTheExistingCookieFormat() {
        CookieSerializer serializer = config.cookieSerializer();
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        serializer.writeCookieValue(new CookieValue(request, response, "legacy-session-id"));

        String setCookie = response.getHeader("Set-Cookie");
        assertThat(setCookie)
                .startsWith("JSESSIONID=")
                .contains("; Path=/; Secure; HttpOnly; SameSite=Lax");

        String cookieValue = setCookie.substring("JSESSIONID=".length(), setCookie.indexOf(';'));
        request.setCookies(new jakarta.servlet.http.Cookie("JSESSIONID", cookieValue));
        assertThat(serializer.readCookieValues(request)).isEqualTo(List.of("legacy-session-id"));
    }

    @Test
    void enablesRedisSessionsOnlyForTheProductionRedisProfile() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(RedisSessionRepository.class);
            assertThat(context).doesNotHaveBean(SessionRepositoryFilter.class);
        });

        contextRunner.withPropertyValues("spring.profiles.active=production-redis")
                .run(context -> {
                    assertThat(context).hasSingleBean(RedisSessionRepository.class);
                    assertThat(context).hasSingleBean(CookieSerializer.class);
                    assertThat(context).hasSingleBean(SessionRepositoryFilter.class);
                });
    }

    @Test
    void batchPoolProfileDoesNotCreateASessionRepositoryOrFilter() {
        new WebApplicationContextRunner()
                .withUserConfiguration(ProductionRedisSessionConfig.class, ProductionPoolEvictionConfig.class)
                .withPropertyValues(
                        "spring.profiles.active=production-pool-evict",
                        "production.pool.experiment=production-db-cutover-v2")
                .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
                .withBean("dataSource", com.zaxxer.hikari.HikariDataSource.class,
                        () -> mock(com.zaxxer.hikari.HikariDataSource.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(ProductionPoolEvictionConfig.PoolControl.class);
                    assertThat(context).doesNotHaveBean(SessionRepository.class);
                    assertThat(context).doesNotHaveBean(SessionRepositoryFilter.class);
                    assertThat(context).doesNotHaveBean(CookieSerializer.class);
                });
    }
}
