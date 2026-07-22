package ch.ubique.backend.job.service.triggers.postgres;

import static org.junit.jupiter.api.Assertions.*;

import ch.ubique.backend.job.checks.PgListenListenerCheck;
import ch.ubique.backend.job.model.JobClassAndId;
import ch.ubique.backend.job.model.triggers.PgListenTrigger;
import ch.ubique.backend.monitoring.check.models.CheckFailed;
import ch.ubique.backend.monitoring.check.models.CheckLevel;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

class PgListenTriggerCheckPostgresTest {

    private static final String CHANNEL = "test_channel";

    private static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17");

    @BeforeAll
    static void startContainer() {
        postgres.start();
    }

    @AfterAll
    static void stopContainer() {
        postgres.stop();
    }

    @Test
    void checkReportsChannelWhenListenerCanNoLongerReachDatabase() throws InterruptedException {
        final var service =
                new PgListenTriggerService(
                        dataSourceProperties(), Duration.ofSeconds(1), Duration.ofSeconds(4));
        final var check = new PgListenListenerCheck(CheckLevel.ERROR, service);
        try {
            // No channels registered yet -> nothing to monitor -> passes.
            assertTrue(check.check().passed());

            // Register a PG_LISTEN trigger -> the listener connects and starts listening.
            service.addTrigger(
                    1,
                    new PgListenTrigger(CHANNEL),
                    new JobClassAndId(1, "ch.ubique.backend.job.TestJob"),
                    arguments -> {});
            waitUntil(Duration.ofSeconds(20), () -> check.check().passed());
            assertTrue(check.check().passed(), "Check should pass while the listener is healthy");

            // The database becomes unreachable -> the listener can no longer interact with it, so
            // after maxSilence the check reports the channel as no longer monitored.
            postgres.stop();
            waitUntil(Duration.ofSeconds(30), () -> !check.check().passed());

            final var status = check.check();
            assertFalse(status.passed());
            final var failed = assertInstanceOf(CheckFailed.class, status);
            final var context = assertInstanceOf(Map.class, failed.context());
            assertEquals(List.of(CHANNEL), context.get("channels"));
        } finally {
            service.shutdown();
        }
    }

    private static DataSourceProperties dataSourceProperties() {
        final var properties = new DataSourceProperties();
        properties.setUrl(postgres.getJdbcUrl());
        properties.setUsername(postgres.getUsername());
        properties.setPassword(postgres.getPassword());
        properties.setDriverClassName(postgres.getDriverClassName());
        return properties;
    }

    @SuppressWarnings({"BusyWait", "java:S2925"})
    private static void waitUntil(final Duration timeout, final BooleanSupplier condition)
            throws InterruptedException {
        final var deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(200);
        }
        fail("Condition not met within " + timeout);
    }
}
