package ch.ubique.backend.job.service.postgres;

import jakarta.annotation.PreDestroy;

import org.springframework.boot.jdbc.DatabaseDriver;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Manages the lifecycle of {@link PgChannelListener}s: at most one listener is kept per PostgreSQL
 * {@code LISTEN} channel, shared between every caller registered on that channel.
 *
 * <p>Not a Spring-managed singleton itself, since two independent registries typically coexist -
 * one for job-defined {@code PG_LISTEN} triggers (application datasource) and one for the job
 * library's own internal queue notifications (job datasource); see {@code
 * PgChannelListenerRegistryConfig}, which produces both as distinctly-qualified beans, each backed
 * by its own datasource provider.
 */
public class PgChannelListenerRegistry {

    private final DataSourceProperties dataSourceProperties;
    private final Duration keepAliveInterval;
    private final Duration maxSilence;

    private final Map<String, PgChannelListener> channelListeners = new ConcurrentHashMap<>();

    public PgChannelListenerRegistry(final DataSourceProperties dataSourceProperties) {
        this(
                dataSourceProperties,
                PgChannelListener.DEFAULT_KEEPALIVE_INTERVAL,
                PgChannelListener.DEFAULT_MAX_SILENCE);
    }

    public PgChannelListenerRegistry(
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

    /**
     * Whether the configured datasource is PostgreSQL, i.e. whether {@link #listen} can be used.
     */
    public boolean isPostgres() {
        return DatabaseDriver.fromJdbcUrl(dataSourceProperties.determineUrl())
                == DatabaseDriver.POSTGRESQL;
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
        if (!isPostgres()) {
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
