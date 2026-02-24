package ch.ubique.backend.job.service.triggers.postgres;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

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
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import javax.sql.DataSource;

class PgChannelListener {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final String channel;

    private final DataSource dataSource;
    private final ExecutorService listenerThread;

    private final Consumer<Collection<String>> callback;

    private volatile boolean running;

    PgChannelListener(
            final String channel,
            final Consumer<Collection<String>> callback,
            final DataSourceProperties dataSourceProperties) {
        this.channel = channel;
        this.callback = callback;
        this.dataSource =
                dataSourceProperties
                        .initializeDataSourceBuilder()
                        .type(SimpleDriverDataSource.class)
                        .build();
        this.listenerThread =
                Executors.newSingleThreadExecutor(
                        new ThreadFactoryBuilder()
                                .setNameFormat("pg-listen-" + channel)
                                .setDaemon(true)
                                .build());

        // Start listening
        running = true;
        listenerThread.submit(this::listenerLoop);
    }

    private void listenerLoop() {
        while (running) {
            Connection connection = null;
            try {
                connection = setupListenConnection();
                listenForNotifications(connection);
            } catch (final Exception e) {
                // Connection lost / DB restart / network hiccup: retry
                sleepWithInterrupt(Duration.ofSeconds(5));
            } finally {
                closeConnection(connection);
            }
        }
    }

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
        while (running && !connection.isClosed()) {
            final var notifications = pgConnection.getNotifications();
            if (notifications != null) {
                processNotifications(notifications);
            }
            sleepWithInterrupt(Duration.ofSeconds(3));
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
        callback.accept(payloads);
    }

    private void closeConnection(final Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            DataSourceUtils.releaseConnection(connection, dataSource);
        } catch (final Exception e) {
            // ignore
        }
    }

    private void sleepWithInterrupt(final Duration sleep) {
        try {
            Thread.sleep(sleep);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    void stop() {
        running = false;
        listenerThread.shutdownNow();
    }
}
