package com.memme.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.memme.controller.auth.AuthenticatedUserSession;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.web.http.CookieHttpSessionIdResolver;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@EnabledIfEnvironmentVariable(named = "TEST_REDIS_ENABLED", matches = "true")
class ProductionRedisSessionIntegrationTest {

    private static LettuceConnectionFactory connectionFactory;
    private static LettuceConnectionFactory controlConnectionFactory;

    @BeforeAll
    static void connectToTestRedis() {
        connectionFactory = connectionFactory("TEST_REDIS_USERNAME", "TEST_REDIS_PASSWORD");
        controlConnectionFactory = connectionFactory(
                "TEST_REDIS_CONTROL_USERNAME", "TEST_REDIS_CONTROL_PASSWORD");
    }

    @AfterAll
    static void closeConnectionFactory() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
        if (controlConnectionFactory != null) {
            controlConnectionFactory.destroy();
        }
    }

    @Test
    void sessionSavedByOneWebInstanceIsReadableAfterReplacementAndLogoutRevokesTheOldId() {
        SessionRepository<Session> firstInstance = repository();
        SessionRepository<Session> secondInstance = repository();
        SessionRepository<Session> replacementInstance = repository();
        Session loginSession = firstInstance.createSession();
        loginSession.setAttribute(AuthenticatedUserSession.SESSION_ATTRIBUTE,
                new AuthenticatedUserSession(10L, 20L));
        String oldSessionId = loginSession.getId();
        firstInstance.save(loginSession);

        Session sharedSession = secondInstance.findById(oldSessionId);
        assertThat(sharedSession).isNotNull();
        assertThat((AuthenticatedUserSession) sharedSession.getAttribute(AuthenticatedUserSession.SESSION_ATTRIBUTE))
                .isEqualTo(new AuthenticatedUserSession(10L, 20L));
        assertThat(replacementInstance.findById(oldSessionId)).isNotNull();

        replacementInstance.deleteById(oldSessionId);
        assertThat(secondInstance.findById(oldSessionId)).isNull();
    }

    @Test
    void independentWebInstancesShareLoginAndLogoutRevokesTheCookie() throws Exception {
        MockMvc firstWebInstance = webInstance();
        MockMvc secondWebInstance = webInstance();
        MockMvc replacementWebInstance = webInstance();

        MvcResult login = firstWebInstance.perform(post("/test/session/login"))
                .andExpect(status().isNoContent())
                .andReturn();
        Cookie loginCookie = cookieFrom(login);

        secondWebInstance.perform(get("/test/session/me").cookie(loginCookie)).andExpect(status().isOk());
        replacementWebInstance.perform(get("/test/session/me").cookie(loginCookie)).andExpect(status().isOk());

        secondWebInstance.perform(post("/test/session/logout").cookie(loginCookie)).andExpect(status().isNoContent());
        replacementWebInstance.perform(get("/test/session/me").cookie(loginCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rotatingTheSessionIdKeepsAuthenticationOnlyUnderTheNewId() {
        SessionRepository<Session> firstInstance = repository();
        SessionRepository<Session> secondInstance = repository();
        Session session = firstInstance.createSession();
        session.setAttribute(AuthenticatedUserSession.SESSION_ATTRIBUTE,
                new AuthenticatedUserSession(11L, 21L));
        firstInstance.save(session);
        String oldSessionId = session.getId();

        String newSessionId = session.changeSessionId();
        firstInstance.save(session);

        assertThat(newSessionId).isNotEqualTo(oldSessionId);
        assertThat(secondInstance.findById(oldSessionId)).isNull();
        assertThat((AuthenticatedUserSession) secondInstance.findById(newSessionId)
                .getAttribute(AuthenticatedUserSession.SESSION_ATTRIBUTE))
                .isEqualTo(new AuthenticatedUserSession(11L, 21L));
    }

    @Test
    void expiresAnIdleSessionAndRejectsTheOldCookie() throws Exception {
        MockMvc webInstance = webInstance(Duration.ofSeconds(1));
        MvcResult login = webInstance.perform(post("/test/session/login"))
                .andExpect(status().isNoContent())
                .andReturn();
        Cookie loginCookie = cookieFrom(login);

        Thread.sleep(1500);

        webInstance.perform(get("/test/session/me").cookie(loginCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void poolControlUsesTheProductionKeysAndAllowedHashCommands() {
        String prefix = ProductionPoolEvictionConfig.PoolControl.PREFIX + "production-db-cutover-v2:";
        String commandKey = prefix + "command";
        String registryKey = prefix + "registry";
        String nonce = "integration-" + UUID.randomUUID();
        String ackKey = prefix + "ack:" + nonce;
        StringRedisTemplate controller = stringTemplate(controlConnectionFactory);
        StringRedisTemplate app = stringTemplate(connectionFactory);
        controller.delete(commandKey);
        controller.delete(registryKey);
        controller.delete(ackKey);
        controller.opsForHash().put(commandKey, "experiment", "production-db-cutover-v2");
        controller.opsForHash().put(commandKey, "action", "soft-evict");
        controller.opsForHash().put(commandKey, "nonce", nonce);
        controller.opsForHash().put(commandKey, "deadline", Long.toString(System.currentTimeMillis() + 8000));
        controller.execute((RedisCallback<Boolean>) connection -> connection.keyCommands()
                .expire(commandKey.getBytes(StandardCharsets.UTF_8), 30));

        var pool = mock(HikariDataSource.class);
        var management = mock(HikariPoolMXBean.class);
        when(pool.getHikariPoolMXBean()).thenReturn(management);
        when(management.getActiveConnections()).thenReturn(2);
        when(management.getTotalConnections()).thenReturn(5);
        try (var poolControl = new ProductionPoolEvictionConfig.PoolControl(
                pool, app, "production-db-cutover-v2", Clock.systemUTC())) {
            poolControl.poll();
            poolControl.poll();

            verify(management, times(1)).softEvictConnections();
            Map<Object, Object> registry = controller.opsForHash().entries(registryKey);
            assertThat(registry).hasSize(1);
            String boot = registry.keySet().iterator().next().toString();
            Map<Object, Object> ack = controller.opsForHash().entries(ackKey);
            assertThat(ack.get(boot)).asString().matches("\\d+,2,5");
        } finally {
            controller.delete(commandKey);
            controller.delete(registryKey);
            controller.delete(ackKey);
        }
    }

    private static SessionRepository<Session> repository() {
        return repository(Duration.ofMinutes(30));
    }

    private static MockMvc webInstance() {
        return webInstance(Duration.ofMinutes(30));
    }

    private static MockMvc webInstance(Duration idleTimeout) {
        var resolver = new CookieHttpSessionIdResolver();
        resolver.setCookieSerializer(new ProductionRedisSessionConfig().cookieSerializer());
        var repository = new ProductionRedisSessionConfig().sessionRepository(connectionFactory);
        repository.setDefaultMaxInactiveInterval(idleTimeout);
        var filter = new SessionRepositoryFilter<>(repository);
        filter.setHttpSessionIdResolver(resolver);
        return MockMvcBuilders.standaloneSetup(new TestSessionController()).addFilters(filter).build();
    }

    private static Cookie cookieFrom(MvcResult result) {
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        String cookieValue = setCookie.substring("JSESSIONID=".length(), setCookie.indexOf(';'));
        return new Cookie("JSESSIONID", cookieValue);
    }

    @SuppressWarnings("unchecked")
    private static SessionRepository<Session> repository(Duration idleTimeout) {
        var repository = new ProductionRedisSessionConfig().sessionRepository(connectionFactory);
        repository.setDefaultMaxInactiveInterval(idleTimeout);
        return (SessionRepository<Session>) (SessionRepository<?>) repository;
    }

    private static LettuceConnectionFactory connectionFactory(String usernameVariable, String passwordVariable) {
        String host = System.getenv().getOrDefault("TEST_REDIS_HOST", "127.0.0.1");
        int port = Integer.parseInt(System.getenv().getOrDefault("TEST_REDIS_PORT", "6379"));
        RedisStandaloneConfiguration server = new RedisStandaloneConfiguration(host, port);
        String username = System.getenv().getOrDefault(usernameVariable, "");
        String password = System.getenv().getOrDefault(passwordVariable, "");
        if (!username.isBlank()) {
            server.setUsername(username);
        }
        if (!password.isBlank()) {
            server.setPassword(RedisPassword.of(password));
        }
        var factory = new LettuceConnectionFactory(server);
        factory.afterPropertiesSet();
        return factory;
    }

    private static StringRedisTemplate stringTemplate(RedisConnectionFactory factory) {
        var template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        return template;
    }

    @RestController
    private static class TestSessionController {

        @PostMapping("/test/session/login")
        ResponseEntity<Void> login(HttpServletRequest request) {
            request.getSession().setAttribute(AuthenticatedUserSession.SESSION_ATTRIBUTE,
                    new AuthenticatedUserSession(13L, 23L));
            return ResponseEntity.noContent().build();
        }

        @GetMapping("/test/session/me")
        ResponseEntity<AuthenticatedUserSession> me(HttpServletRequest request) {
            var session = request.getSession(false);
            if (session == null || !(session.getAttribute(AuthenticatedUserSession.SESSION_ATTRIBUTE)
                    instanceof AuthenticatedUserSession principal)) {
                return ResponseEntity.status(401).build();
            }
            return ResponseEntity.ok(principal);
        }

        @PostMapping("/test/session/logout")
        ResponseEntity<Void> logout(HttpServletRequest request) {
            var session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            return ResponseEntity.noContent().build();
        }
    }
}
