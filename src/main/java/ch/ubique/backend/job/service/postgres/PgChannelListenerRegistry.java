package ch.ubique.backend.job.service.postgres;

import ch.ubique.backend.job.model.spring.PgListenDataSourceProvider;

import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.DatabaseDriver;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Manages the lifecycle of {@link PgChannelListener}s: at most one listener is kept per PostgreSQL
 * {@code LISTEN} channel, shared between every caller registered on that channel. Used both by
 * job-specific {@code PG_LISTEN} triggers and by other components that need to react to a {@code
 * NOTIFY} on a given channel without being tied to a specific job.
 */
@Service
@Profile("!job-run")
public class PgChannelListenerRegistry {

    private final DataSourceProperties dataSourceProperties;
    private final Duration keepAliveInterval;
    private final Duration maxSilence;

    private final Map<String, PgChannelListener> channelListeners = new ConcurrentHashMap<>();

    @Autowired
    public PgChannelListenerRegistry(final PgListenDataSourceProvider pgListenDataSourceProvider) {
        this(
                pgListenDataSourceProvider.getDataSourceProperties(),
                PgChannelListener.DEFAULT_KEEPALIVE_INTERVAL,
                PgChannelListener.DEFAULT_MAX_SILENCE);
    }

    PgChannelListenerRegistry(
            final DataSourceProperties dataSourceProperties,
            final Duration keepAliveInterval,
            final Duration maxSilence) {
        this.dataSourceProperties = dataSourceProperties;
        this.keepAliveInterval = keepAliveInterval;
        this.maxSilence = maxSilence;
    }

    /**
     * Ensures a listener is running for the given channel, creating one on first use. If a listener
     * for this channel already exists, it keeps using its original callback and {@code onEvents} is
     * ignored.
     */
    public void listen(final String channel, final Consumer<Collection<String>> onEvents) {
        channelListeners.computeIfAbsent(channel, c -> createChannelListener(c, onEvents));
    }

    /** Stops and removes the listener for the given channel, if one is running. */
    public void stopListening(final String channel) {
        final var listener = channelListeners.remove(channel);
        if (listener != null) {
            listener.stop();
        }
    }

    private PgChannelListener createChannelListener(
            final String channel, final Consumer<Collection<String>> onEvents) {
        final var url = dataSourceProperties.determineUrl();
        final var driver = DatabaseDriver.fromJdbcUrl(url);
        if (driver != DatabaseDriver.POSTGRESQL) {
            throw new IllegalArgumentException(
                    "Only PostgreSQL is supported for listening to PostgreSQL channels.");
        }
        return new PgChannelListener(
                channel, onEvents, dataSourceProperties, keepAliveInterval, maxSilence);
    }

    /**
     * Returns the channels whose listener is no longer actively monitoring the database (dead
     * listener thread or a connection that cannot be re-established). An empty collection means all
     * registered channels are being listened to.
     */
    public Collection<String> unhealthyChannels() {
        return channelListeners.entrySet().stream()
                .filter(entry -> !entry.getValue().isHealthy())
                .map(Map.Entry::getKey)
                .toList();
    }

    @PreDestroy
    public void shutdown() {
        channelListeners.values().forEach(PgChannelListener::stop);
    }
}
