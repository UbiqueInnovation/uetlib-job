package ch.uetlib.job.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

class JobRunStateTest {

    @Test
    void allStatesCategorized() {
        final var allStates = List.of(JobRunState.values());
        final var categorizedStates =
                Stream.of(
                                JobRunState.RUNNING_STATES,
                                JobRunState.SUCCESS_STATES,
                                JobRunState.PROBLEM_STATES)
                        .flatMap(Collection::stream)
                        .toList();
        assertThat(categorizedStates).hasSameElementsAs(allStates);
    }

    @ParameterizedTest
    @EnumSource(JobRunState.class)
    void onlyOneCategoryIsTrue(final JobRunState state) {
        assertThat(List.of(state.isSuccess(), state.isProblem(), state.isRunning()))
                .filteredOn(b -> b)
                .hasSize(1);
    }

    @ParameterizedTest
    @EnumSource(JobRunState.class)
    void acknowledgeableIffProblem(final JobRunState state) {
        assertThat(state.isProblem()).isEqualTo(state.isAcknowledgeable());
    }
}
