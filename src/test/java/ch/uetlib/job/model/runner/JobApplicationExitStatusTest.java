package ch.uetlib.job.model.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class JobApplicationExitStatusTest {

    @ParameterizedTest
    @EnumSource(JobApplicationExitStatus.class)
    void exitCode(final JobApplicationExitStatus value) {
        if (value == JobApplicationExitStatus.SUCCESS) {
            assertEquals(0, value.exitCode());
            return;
        }
        assertThat(value.exitCode()).isNotZero().isPositive();
    }

    @Test
    void distinctExitCodes() {
        assertThat(JobApplicationExitStatus.values())
                .extracting(JobApplicationExitStatus::exitCode)
                .doesNotHaveDuplicates();
    }
}
