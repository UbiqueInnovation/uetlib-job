package ch.ubique.backend.job.checks;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.ubique.backend.job.service.postgres.PgChannelListenerRegistry;
import ch.ubique.backend.monitoring.check.models.CheckFailed;
import ch.ubique.backend.monitoring.check.models.CheckLevel;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class PgListenListenerCheckTest {

    private final PgChannelListenerRegistry channelListenerRegistry =
            mock(PgChannelListenerRegistry.class);
    private final PgListenListenerCheck check =
            new PgListenListenerCheck(CheckLevel.ERROR, channelListenerRegistry);

    @Test
    void passesWhenAllListenersHealthy() {
        when(channelListenerRegistry.unhealthyChannels()).thenReturn(List.of());

        final var status = check.check();

        assertTrue(status.passed());
    }

    @Test
    void failsAndReportsUnhealthyChannels() {
        when(channelListenerRegistry.unhealthyChannels())
                .thenReturn(List.of("new_dataset_run", "other_channel"));

        final var status = check.check();

        assertFalse(status.passed());
        final var failed = assertInstanceOf(CheckFailed.class, status);
        final var context = assertInstanceOf(Map.class, failed.context());
        assertEquals(List.of("new_dataset_run", "other_channel"), context.get("channels"));
    }
}
