package ch.uetlib.job.model.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class JobPropertyIdTest {

    private static JobPropertyId id(final int jobId, final String key) {
        final var id = new JobPropertyId();
        id.setJobId(jobId);
        id.setKey(key);
        return id;
    }

    @Test
    void equalsComparesJobIdAndKey() {
        final var id = id(1, "a");

        assertEquals(id, id(1, "a"));
        assertEquals(id.hashCode(), id(1, "a").hashCode());
        assertNotEquals(id, id(2, "a"));
        assertNotEquals(id, id(1, "b"));
        assertNotEquals(id, "a");
    }
}
