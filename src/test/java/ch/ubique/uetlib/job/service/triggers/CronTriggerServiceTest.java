package ch.ubique.uetlib.job.service.triggers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.ubique.uetlib.core.utils.TimeUtils;
import ch.ubique.uetlib.job.model.triggers.CronTrigger;
import ch.ubique.uetlib.job.model.triggers.TriggerType;
import io.netty.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

class CronTriggerServiceTest {

    private final CronTriggerService triggerService =
            new CronTriggerService(TimeUtils.ZONE_NAME_UTC, taskScheduler());

    @SuppressWarnings("unchecked")
    private static TaskScheduler taskScheduler() {
        final var taskScheduler = mock(TaskScheduler.class);
        when(taskScheduler.schedule(Mockito.any(), (Trigger) Mockito.any()))
                .thenReturn(mock(ScheduledFuture.class));
        return taskScheduler;
    }

    @Test
    void onStartup() {
        final var runCount = new AtomicInteger();
        triggerService.onStartup(
                0,
                new CronTrigger("0 0 2 * * ?"),
                null,
                jobRunArguments -> runCount.incrementAndGet());
        assertEquals(0, runCount.get());
    }

    @Test
    void addTrigger() {
        assertDoesNotThrow(
                () ->
                        triggerService.addTrigger(
                                0, new CronTrigger("0 0 2 * * ?"), null, jobRunArguments -> {}));
    }

    @Test
    void removeTrigger() {
        assertDoesNotThrow(() -> triggerService.removeTrigger(0));
    }

    @Test
    void triggerType() {
        assertEquals(TriggerType.CRON, triggerService.triggerType());
    }
}
