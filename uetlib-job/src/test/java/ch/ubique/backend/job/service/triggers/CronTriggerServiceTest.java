package ch.ubique.backend.job.service.triggers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.ubique.backend.job.model.triggers.CronTriggerDefinition;
import ch.ubique.backend.job.model.triggers.TriggerType;

import io.netty.util.concurrent.ScheduledFuture;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

import java.util.concurrent.atomic.AtomicInteger;

class CronTriggerServiceTest {

    private final CronTriggerService triggerService =
            new CronTriggerService("UTC", taskScheduler());

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
                0, new CronTriggerDefinition("0 0 2 * * ?"), runCount::incrementAndGet);
        assertEquals(0, runCount.get());
    }

    @Test
    void addTrigger() {
        assertDoesNotThrow(
                () ->
                        triggerService.addTrigger(
                                0, new CronTriggerDefinition("0 0 2 * * ?"), () -> {}));
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
