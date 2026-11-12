package ch.uetlib.job.model.entity;

import static org.junit.jupiter.api.Assertions.*;

import ch.uetlib.job.testjob.TestJob;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JobEntityTest {

    @Test
    void newForClassIsEnabledByDefaultWithTheGivenClass() {
        final var entity = JobEntity.newForClass(TestJob.class);

        assertTrue(entity.isEnabled());
        assertEquals(TestJob.class.getCanonicalName(), entity.canonicalClassName());
    }

    @Test
    void setEnabledTogglesEnabled() {
        final var entity = JobEntity.newForClass(TestJob.class);

        entity.setEnabled(false);

        assertFalse(entity.isEnabled());
    }

    @Test
    void toRecordMapsIdAndClassName() {
        final var entity = JobEntity.newForClass(TestJob.class);
        ReflectionTestUtils.setField(entity, "id", 7);

        final var rec = entity.toRecord();

        assertEquals(7, rec.id());
        assertEquals(TestJob.class.getCanonicalName(), rec.canonicalClassName());
    }
}
