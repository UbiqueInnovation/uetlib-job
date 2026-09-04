package ch.uetlib.job.service.postgres;

import jakarta.annotation.PreDestroy;

import org.springframework.boot.jdbc.DatabaseDriver;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * One shared {@link PgChannelListener} per channel. Not a singleton: triggers and the queue each
 * get their own (see {@code PgChannelListenerRegistryConfig}).
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
     * Starts a listener for {@code channel} on first use; later callers' {@code onEvents} are
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
