package ch.ubique.backend.job.service.triggers;

import static org.junit.jupiter.api.Assertions.*;

import ch.ubique.backend.job.model.triggers.TriggerType;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

class RunOnStartupTriggerServiceTest {

    private final StartupTriggerService triggerService = new StartupTriggerService();

    @Test
    void onStartup() {
        final var runCount = new AtomicInteger();
        triggerService.onStartup(0, null, null, jobRunArguments -> runCount.incrementAndGet());
        assertEquals(1, runCount.get());
    }

    @Test
    void addTrigger() {
        assertDoesNotThrow(() -> triggerService.addTrigger(0, null, null, null));
    }

    @Test
    void removeTrigger() {
        assertDoesNotThrow(() -> triggerService.removeTrigger(-1));
    }

    @Test
    void triggerType() {
        assertEquals(TriggerType.STARTUP, triggerService.triggerType());
    }
}
