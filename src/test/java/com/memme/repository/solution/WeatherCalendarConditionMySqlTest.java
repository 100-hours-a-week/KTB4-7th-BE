package com.memme.repository.solution;

import com.memme.entity.solution.WeatherCalendarCondition;
import com.memme.entity.solution.WeatherCalendarConditionEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.LocalDate;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "WEATHER_CALENDAR_MYSQL_TEST_URL", matches = ".+/weather_calendar_test\\?.+")
class WeatherCalendarConditionMySqlTest {
    private static String url;
    private static String adminUrl;
    private static SessionFactory factory;
    private static boolean ownsSchema;

    @BeforeAll
    static void prepare() throws Exception {
        url = System.getenv("WEATHER_CALENDAR_MYSQL_TEST_URL");
        if (!url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/weather_calendar_test\\?.+")) {
            throw new IllegalArgumentException("isolated loopback weather_calendar_test DB required");
        }
        adminUrl = url.replace("/weather_calendar_test?", "/mysql?");
        try (var connection = DriverManager.getConnection(adminUrl, "root", "");
             var statement = connection.createStatement()) {
            try (var version = statement.executeQuery("SELECT VERSION()")) {
                version.next();
                assertThat(version.getString(1)).startsWith("8.4.");
            }
            // Fail if the schema already exists rather than touching another test's data.
            statement.execute("CREATE DATABASE weather_calendar_test");
            ownsSchema = true;
        }
        try (var connection = DriverManager.getConnection(url, "root", "");
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE weather_stations (id BIGINT UNSIGNED PRIMARY KEY)");
            statement.execute("INSERT INTO weather_stations (id) VALUES (108), (119)");
            String migration = Files.readString(Path.of("docker/mysql/migrations/20261026-add-sales-calendar.sql"));
            statement.execute(migration.substring(migration.indexOf("CREATE TABLE weather_calendar_conditions")));
        }
        factory = new Configuration().addAnnotatedClass(WeatherCalendarConditionEntity.class)
                .setProperty("hibernate.connection.driver_class", "com.mysql.cj.jdbc.Driver")
                .setProperty("hibernate.connection.url", url)
                .setProperty("hibernate.connection.username", "root")
                .setProperty("hibernate.connection.password", "")
                .setProperty("hibernate.hbm2ddl.auto", "none")
                .buildSessionFactory();
    }

    @AfterAll
    static void cleanup() throws Exception {
        if (factory != null) factory.close();
        if (ownsSchema) {
            try (var connection = DriverManager.getConnection(adminUrl, "root", "");
                 var statement = connection.createStatement()) {
                statement.execute("DROP DATABASE weather_calendar_test");
            }
        }
    }

    @ParameterizedTest
    @EnumSource(WeatherCalendarCondition.class)
    void savesAndReadsActualQuotedColumnOnMySql(WeatherCalendarCondition condition) throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 20).plusDays(condition.ordinal());
        Long id;
        try (var session = factory.openSession()) {
            var transaction = session.beginTransaction();
            var entity = WeatherCalendarConditionEntity.create(108L, date, condition, date.atTime(12, 0), "KMA_ASOS");
            session.persist(entity);
            session.flush();
            id = entity.getId();
            transaction.commit();
        }
        // A new persistence context forces a real SELECT instead of first-level cache access.
        try (var session = factory.openSession()) {
            var loaded = session.find(WeatherCalendarConditionEntity.class, id);
            assertThat(loaded.getCondition()).isEqualTo(condition);
            assertThat(loaded.getObservationDate()).isEqualTo(date);
            assertThat(loaded.getObservedAt()).isEqualTo(date.atTime(12, 0));
            assertThat(loaded.getSource()).isEqualTo("KMA_ASOS");
            var dates = session.createQuery("from WeatherCalendarConditionEntity w where w.stationId = :station and w.observationDate = :date", WeatherCalendarConditionEntity.class)
                    .setParameter("station", 108L).setParameter("date", date).getResultList();
            assertThat(dates).hasSize(1);
        }
        try (var connection = DriverManager.getConnection(url, "root", "");
             var statement = connection.prepareStatement("SELECT `condition` FROM weather_calendar_conditions WHERE id = ?")) {
            statement.setLong(1, id);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo(condition.name());
            }
        }
    }
}
