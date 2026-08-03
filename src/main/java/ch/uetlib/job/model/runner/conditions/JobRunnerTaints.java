package ch.uetlib.job.model.runner.conditions;

public final class JobRunnerTaints {
    public static final JobRunnerTaint UB_KUB_DEV_TAINT = new JobRunnerTaint("ub-kub-dev");
    public static final JobRunnerTaint DEDICATED_K8S_TAINT = new JobRunnerTaint("dedicated-k8s");

    private JobRunnerTaints() {}
}
