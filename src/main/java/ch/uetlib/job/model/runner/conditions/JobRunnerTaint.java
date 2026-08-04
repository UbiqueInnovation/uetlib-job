package ch.uetlib.job.model.runner.conditions;

import com.google.common.collect.MoreCollectors;

import java.util.Arrays;

public record JobRunnerTaint(String value) {
    public static JobRunnerTaint from(final String value) {
        return Arrays.stream(JobRunnerTaints.values())
                .filter(t -> t.value().equals(value))
                .collect(MoreCollectors.toOptional())
                .map(JobRunnerTaints::taint)
                .orElseGet(() -> new JobRunnerTaint(value));
    }
}
