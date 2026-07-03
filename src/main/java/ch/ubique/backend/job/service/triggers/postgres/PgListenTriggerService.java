package ch.ubique.backend.job.service.triggers.postgres;

import ch.ubique.backend.job.model.JobClassAndId;
import ch.ubique.backend.job.model.JobRunArguments;
import ch.ubique.backend.job.model.spring.PgListenDataSourceProvider;
import ch.ubique.backend.job.model.triggers.PgListenTrigger;
import ch.ubique.backend.job.model.triggers.TriggerType;
import ch.ubique.backend.job.service.triggers.TriggerService;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.DatabaseDriver;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

@Service
@Profile("!job-run")
public class PgListenTriggerService implements TriggerService<PgListenTrigger> {

    private static final Logger logger = LoggerFactory.getLogger(PgListenTriggerService.class);

    private final DataSourceProperties dataSourceProperties;
    private final Duration keepAliveInterval;
    private final Duration maxSilence;

    private final Map<String, PgChannelListener> channelListeners = new ConcurrentHashMap<>();
    private final Collection<TriggerInfo> triggerInfos = new ConcurrentLinkedQueue<>();

    @Autowired
    public PgListenTriggerService(final PgListenDataSourceProvider pgListenDataSourceProvider) {
        this(
                pgListenDataSourceProvider.getDataSourceProperties(),
                PgChannelListener.DEFAULT_KEEPALIVE_INTERVAL,
                PgChannelListener.DEFAULT_MAX_SILENCE);
    }

    PgListenTriggerService(
            final DataSourceProperties dataSourceProperties,
            final Duration keepAliveInterval,
            final Duration maxSilence) {
        this.dataSourceProperties = dataSourceProperties;
        this.keepAliveInterval = keepAliveInterval;
        this.maxSilence = maxSilence;
    }

    @Override
    public void addTrigger(
            final int triggerId,
            final PgListenTrigger triggerDefinition,
            final JobClassAndId job,
            final Consumer<JobRunArguments> consumer) {
        channelListeners.computeIfAbsent(triggerDefinition.channel(), this::createChannelListener);
        triggerInfos.add(new TriggerInfo(triggerId, job, triggerDefinition, consumer));
    }

    private PgChannelListener createChannelListener(final String channel) {
        final var url = dataSourceProperties.determineUrl();
        final var driver = DatabaseDriver.fromJdbcUrl(url);
        if (driver != DatabaseDriver.POSTGRESQL) {
            throw new IllegalArgumentException(
                    "Only PostgreSQL is supported for listening to PostgreSQL channels.");
        }
        return new PgChannelListener(
                channel,
                payloads -> onEvents(channel, payloads),
                dataSourceProperties,
                keepAliveInterval,
                maxSilence);
    }

    private void onEvents(final String channel, final Collection<String> payloads) {
        for (final var triggerInfo : triggerInfos) {
            if (triggerInfo.matches(channel, payloads)) {
                executeTrigger(triggerInfo);
            }
        }
    }

    private static void executeTrigger(final TriggerInfo triggerInfo) {
        final var consumer = triggerInfo.callback();
        final var arguments = new JobRunArguments(false, TriggerType.PG_LISTEN, null, null, null);
        logger.debug("Calling trigger callback for {}.", triggerInfo.job().name());
        consumer.accept(arguments);
    }

    @Override
    public void removeTrigger(final int triggerId) {
        final var triggerInfoMaybe =
                triggerInfos.stream().filter(t -> t.triggerId() == triggerId).findFirst();

        if (triggerInfoMaybe.isEmpty()) {
            logger.debug("No trigger found for id {}.", triggerId);
            return;
        }
        final var triggerInfo = triggerInfoMaybe.get();
        triggerInfos.remove(triggerInfo);
        logger.debug("Removed trigger {}.", triggerInfo);

        final var channel = triggerInfo.channel();
        final var remainingChannels = triggerInfos.stream().map(TriggerInfo::channel).toList();
        if (!remainingChannels.contains(channel)) {
            channelListeners.get(channel).stop();
            channelListeners.remove(channel);
            logger.debug("Removed channel listener for {}.", channel);
        }
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

    private record TriggerInfo(
            int triggerId,
            JobClassAndId job,
            PgListenTrigger trigger,
            Consumer<JobRunArguments> callback) {

        private String channel() {
            return trigger.channel();
        }

        private boolean matches(final String channel, final Collection<String> payloads) {
            return channel().equals(channel) && payloads.stream().anyMatch(trigger::matches);
        }

        @Override
        public boolean equals(final Object o) {
            return this == o
                    || (o instanceof final TriggerInfo info && info.triggerId == triggerId);
        }

        @Override
        public int hashCode() {
            return triggerId;
        }
    }
}
