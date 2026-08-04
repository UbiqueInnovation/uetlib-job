package ch.ubique.backend.job.service.triggers.postgres;

import ch.ubique.backend.job.model.JobClassAndId;
import ch.ubique.backend.job.model.JobRunArguments;
import ch.ubique.backend.job.model.triggers.PgListenTrigger;
import ch.ubique.backend.job.model.triggers.TriggerType;
import ch.ubique.backend.job.service.postgres.PgChannelListenerRegistry;
import ch.ubique.backend.job.service.triggers.TriggerService;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

@Service
@Profile("!job-run")
public class PgListenTriggerService implements TriggerService<PgListenTrigger> {

    private static final Logger logger = LoggerFactory.getLogger(PgListenTriggerService.class);

    private final PgChannelListenerRegistry channelListenerRegistry;

    private final Collection<TriggerInfo> triggerInfos = new ConcurrentLinkedQueue<>();

    public PgListenTriggerService(final PgChannelListenerRegistry channelListenerRegistry) {
        this.channelListenerRegistry = channelListenerRegistry;
    }

    @Override
    public void addTrigger(
            final int triggerId,
            final PgListenTrigger triggerDefinition,
            final JobClassAndId job,
            final Consumer<JobRunArguments> consumer) {
        final var channel = triggerDefinition.channel();
        channelListenerRegistry.listen(channel, payloads -> onEvents(channel, payloads));
        triggerInfos.add(new TriggerInfo(triggerId, job, triggerDefinition, consumer));
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
            channelListenerRegistry.stopListening(channel);
            logger.debug("Removed channel listener for {}.", channel);
        }
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
            if (this == o) {
                return true;
            }

            if (!(o instanceof final TriggerInfo that)) {
                return false;
            }

            return new EqualsBuilder().append(triggerId, that.triggerId).isEquals();
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder(17, 37).append(triggerId).toHashCode();
        }
    }
}
