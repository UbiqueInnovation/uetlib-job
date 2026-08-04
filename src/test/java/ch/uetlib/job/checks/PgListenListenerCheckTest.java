package ch.uetlib.job.checks;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.uetlib.job.service.postgres.PgChannelListenerRegistry;
import ch.uetlib.monitoring.check.models.CheckFailed;
import ch.uetlib.monitoring.check.models.CheckLevel;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class PgListenListenerCheckTest {

    private final PgChannelListenerRegistry channelListenerRegistry =
            mock(PgChannelListenerRegistry.class);
    private final PgListenListenerCheck check =
            new PgListenListenerCheck(CheckLevel.ERROR, List.of(channelListenerRegistry));

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

    @Test
    void aggregatesUnhealthyChannelsAcrossBothRegistries() {
        // The job-trigger registry (application datasource) and the job-queue registry (job
        // datasource) are independent beans; an outage on either must be reported.
        final var otherRegistry = mock(PgChannelListenerRegistry.class);
        final var checkOverBoth =
                new PgListenListenerCheck(
                        CheckLevel.ERROR, List.of(channelListenerRegistry, otherRegistry));
        when(channelListenerRegistry.unhealthyChannels()).thenReturn(List.of("app_channel"));
        when(otherRegistry.unhealthyChannels()).thenReturn(List.of("new_job_queued_run"));

        final var status = checkOverBoth.check();

        assertFalse(status.passed());
        final var failed = assertInstanceOf(CheckFailed.class, status);
        final var context = assertInstanceOf(Map.class, failed.context());
        assertEquals(List.of("app_channel", "new_job_queued_run"), context.get("channels"));
    }
}
