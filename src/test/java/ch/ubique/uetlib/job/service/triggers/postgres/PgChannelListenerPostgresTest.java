package ch.ubique.uetlib.job.service.triggers.postgres;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.DriverManager;
import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

class PgChannelListenerPostgresTest {

    private static final String CHANNEL = "test_channel";

    private static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    @BeforeAll
    static void startContainer() {
        postgres.start();
    }

    @AfterAll
    static void stopContainer() {
        postgres.stop();
    }

    @Test
    void receivesNotificationAndReportsHealthy() throws InterruptedException {
        final var received = new CopyOnWriteArrayList<String>();
        final var listener =
                new PgChannelListener(
                        CHANNEL,
                        received::addAll,
                        dataSourceProperties(),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(30));
        try {
            // A NOTIFY is only delivered to sessions that issued LISTEN beforehand, so we retry
            // until the listener has connected and our notification is picked up.
            waitUntil(
                    Duration.ofSeconds(30),
                    () -> {
                        sendNotify("go");
                        return received.contains("go");
                    });

            assertTrue(received.contains("go"), "Listener should have received the notification");
            assertTrue(listener.isHealthy(), "A running, connected listener must report healthy");
        } finally {
            listener.stop();
        }

        assertFalse(listener.isHealthy(), "A stopped listener must report unhealthy");
    }

    private static DataSourceProperties dataSourceProperties() {
        final var properties = new DataSourceProperties();
        properties.setUrl(postgres.getJdbcUrl());
        properties.setUsername(postgres.getUsername());
        properties.setPassword(postgres.getPassword());
        properties.setDriverClassName(postgres.getDriverClassName());
        return properties;
    }

    private static void sendNotify(final String payload) {
        try (final var connection =
                        DriverManager.getConnection(
                                postgres.getJdbcUrl(),
                                postgres.getUsername(),
                                postgres.getPassword());
                final var statement = connection.createStatement()) {
            statement.execute("NOTIFY " + CHANNEL + ", '" + payload + "'");
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings({"BusyWait", "java:S2925"})
    private static void waitUntil(final Duration timeout, final BooleanSupplier condition)
            throws InterruptedException {
        final var deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(500);
        }
        fail("Condition not met within " + timeout);
    }
}
