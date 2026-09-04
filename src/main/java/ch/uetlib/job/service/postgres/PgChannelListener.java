package ch.uetlib.job.service.postgres;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import org.jspecify.annotations.Nullable;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import javax.sql.DataSource;

class PgChannelListener {

    private static final Duration NOTIFICATION_WAIT_TIME = Duration.ofSeconds(15);
    private static final Duration RECONNECT_DELAY = Duration.ofSeconds(5);

    /**
     * A {@code LISTEN} connection is mostly idle (it only receives data when a {@code NOTIFY}
     * arrives) and is therefore prone to being silently dropped by firewalls/NAT idle timeouts. A
     * non-blocking {@link PGConnection#getNotifications()} poll does no network I/O while idle, so
     * such a half-open connection would never be detected and notifications would be lost forever.
     * To detect this we periodically run a keepalive query that forces a round-trip: on a dead
     * socket it throws (within {@link #SOCKET_TIMEOUT}) and triggers a reconnect.
     */
    static final Duration DEFAULT_KEEPALIVE_INTERVAL = Duration.ofSeconds(30);

    /**
     * The listener is considered unhealthy if it has not had a confirmed interaction with the
     * database (successful connect, keepalive or received notification) within this window. Must be
     * larger than {@link #DEFAULT_KEEPALIVE_INTERVAL} to tolerate a few missed keepalives / a
     * reconnect.
     */
    static final Duration DEFAULT_MAX_SILENCE = Duration.ofSeconds(90);

    private static final Duration SOCKET_TIMEOUT = Duration.ofSeconds(30);

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final String channel;

    private final DataSource dataSource;
    private final ExecutorService listenerThread;

    private final Consumer<Collection<String>> callback;

    private final Duration keepAliveInterval;
    private final Duration maxSilence;

    private volatile boolean running;
    private volatile Instant lastInteraction = Instant.now();

    PgChannelListener(
            final String channel,
            final Consumer<Collection<String>> callback,
            final DataSourceProperties dataSourceProperties,
            final Duration keepAliveInterval,
            final Duration maxSilence) {
        this.channel = channel;
        this.callback = callback;
        this.keepAliveInterval = keepAliveInterval;
        this.maxSilence = maxSilence;
        this.dataSource = buildDataSource(dataSourceProperties);
        this.listenerThread =
                Executors.newSingleThreadExecutor(
                        new ThreadFactoryBuilder()
                                .setNameFormat("pg-listen-" + channel)
                                .setDaemon(true)
                                .build());

        // Start listening
        running = true;
        listenerThread.execute(this::listenerLoop);
    }

    private static DataSource buildDataSource(final DataSourceProperties dataSourceProperties) {
        final var dataSource =
                dataSourceProperties
                        .initializeDataSourceBuilder()
                        .type(SimpleDriverDataSource.class)
                        .build();
        // Enable OS-level keepalive and bound socket reads so a dropped connection eventually
        // surfaces as an exception instead of hanging the listener forever.
        final var connectionProperties = new Properties();
        connectionProperties.setProperty("tcpKeepAlive", String.valueOf(true));
        connectionProperties.setProperty(
                "socketTimeout", String.valueOf(SOCKET_TIMEOUT.toSeconds()));
        dataSource.setConnectionProperties(connectionProperties);
        return dataSource;
    }

    private void listenerLoop() {
        while (running) {
            Connection connection = null;
            try {
                connection = setupListenConnection();
                markInteraction();
                listenForNotifications(connection);
            } catch (final Exception e) {
                // Connection lost / DB restart / network hiccup / dead socket: retry
                logger.warn(
                        "Listener on channel {} disconnected, reconnecting in {}s: {}",
                        channel,
                        RECONNECT_DELAY.toSeconds(),
                        e.toString());
                sleepWithInterrupt(RECONNECT_DELAY);
            } finally {
                closeConnection(connection);
            }
        }
    }

    @SuppressWarnings("java:S2077")
    private Connection setupListenConnection() throws SQLException {
        final var connection = DataSourceUtils.getConnection(dataSource);
        connection.setAutoCommit(true);

        try (final var statement = connection.createStatement()) {
            statement.execute("LISTEN " + channel);
        }
        return connection;
    }

    private void listenForNotifications(final Connection connection) throws SQLException {
        logger.info("Listening for notifications on channel {}", channel);
        final var pgConnection = connection.unwrap(PGConnection.class);
        var lastKeepAlive = Instant.now();
        while (running && !connection.isClosed()) {
            final var notifications =
                    pgConnection.getNotifications((int) NOTIFICATION_WAIT_TIME.toMillis());
            if (notifications != null) {
                processNotifications(notifications);
                markInteraction();
            }

            final var now = Instant.now();
            if (now.minus(keepAliveInterval).isAfter(lastKeepAlive)) {
                sendKeepAlive(connection);
                markInteraction();
                lastKeepAlive = now;
            }
        }
    }

    /**
     * Forces a round-trip to the database so that a silently dropped (half-open) connection throws
     * instead of leaving the listener stuck on a dead socket.
     */
    private void sendKeepAlive(final Connection connection) throws SQLException {
        try (final var statement = connection.createStatement()) {
            statement.execute("SELECT 1");
        }
    }

    private void processNotifications(final PGNotification[] notifications) {
        final var payloads =
                Arrays.stream(notifications)
                        .filter(n -> channel.equals(n.getName()))
                        .map(PGNotification::getParameter)
                        .distinct()
                        .toList();
        if (payloads.isEmpty()) {
            return;
        }
        logger.debug("{} received: {}", channel, payloads);
        try {
            callback.accept(payloads);
        } catch (final Exception e) {
            logger.warn(
                    "Error while processing notifications callback on channel {} (CB function: {})",
                    channel,
                    callback,
                    e);
        }
    }

    private void markInteraction() {
        lastInteraction = Instant.now();
    }

    /**
     * Whether the listener is actively monitoring its channel, i.e. it is running and has had a
     * confirmed interaction with the database within {@link #maxSilence}. A stale value indicates a
     * dead listener thread or a connection that cannot be re-established.
     */
    boolean isHealthy() {
        if (!running) {
            return false;
        }
        final var silentSince = lastInteraction.plus(maxSilence);
        return Instant.now().isBefore(silentSince);
    }

    private void closeConnection(final @Nullable Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            DataSourceUtils.releaseConnection(connection, dataSource);
        } catch (final Exception _) {
            // ignore
        }
    }

    private void sleepWithInterrupt(final Duration sleep) {
        try {
            Thread.sleep(sleep);
        } catch (final InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }

    void stop() {
        running = false;
        listenerThread.shutdownNow();
    }
}
