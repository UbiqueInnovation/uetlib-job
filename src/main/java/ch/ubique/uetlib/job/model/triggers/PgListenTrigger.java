package ch.ubique.uetlib.job.model.triggers;

import java.util.Collection;
import java.util.List;

/**
 * @param channel PostgresSQL channel to listen on
 * @param payloads Payloads for which the trigger should be fired, an empty list means listening to
 *     all payloads
 */
public record PgListenTrigger(String channel, Collection<String> payloads)
        implements TriggerDefinition {

    public PgListenTrigger(final String channel) {
        this(channel, List.of());
    }

    public boolean matches(final String payload) {
        return payloads.isEmpty() || payloads.contains(payload);
    }
}
