package ch.ubique.uetlib.job.service.triggers.postgres;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;

class PgChannelListenerTimeoutTest {

    @Test
    void reportsUnhealthyAfterSilenceTimeout() throws InterruptedException {
        // Point the listener at an unreachable database so it can never confirm an interaction.
        final var properties = new DataSourceProperties();
        properties.setUrl("jdbc:postgresql://127.0.0.1:1/unreachable");
        properties.setUsername("user");
        properties.setPassword("password");
        properties.setDriverClassName("org.postgresql.Driver");

        final var listener =
                new PgChannelListener(
                        "test_channel",
                        payloads -> {},
                        properties,
                        Duration.ofMillis(100), // keepAliveInterval
                        Duration.ofSeconds(1)); // maxSilence
        try {
            // Freshly created it is still within the grace window, so it reports healthy.
            assertTrue(listener.isHealthy());

            // It can never reach the database, so once maxSilence elapses without a successful
            // interaction the listener must report unhealthy.
            waitUntil(Duration.ofSeconds(5), () -> !listener.isHealthy());
            assertFalse(listener.isHealthy());
        } finally {
            listener.stop();
        }
    }

    private static void waitUntil(final Duration timeout, final BooleanSupplier condition)
            throws InterruptedException {
        final var deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        fail("Condition not met within " + timeout);
    }
}
