package ch.ubique.backend.job.model.runner.conditions;

public enum JobRunnerTaints {
    UB_KUB_DEV_TAINT("ub-kub-dev"),
    DEDICATED_K8S_TAINT("dedicated-k8s");

    private final JobRunnerTaint taint;

    JobRunnerTaints(final String value) {
        this.taint = new JobRunnerTaint(value);
    }

    public JobRunnerTaint taint() {
        return taint;
    }

    public String value() {
        return taint().value();
    }
}
