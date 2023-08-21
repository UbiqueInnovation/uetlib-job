package ch.ubique.backend.job.model;

public record JobRunArguments(boolean forceRun) {

    public static JobRunArguments DEFAULT = new JobRunArguments(false);
}
