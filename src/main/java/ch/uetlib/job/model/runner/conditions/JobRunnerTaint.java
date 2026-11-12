package ch.uetlib.job.model.runner.conditions;

public record JobRunnerTaint(String value) {
    public static JobRunnerTaint from(final String value) {
        return new JobRunnerTaint(value);
    }
}
