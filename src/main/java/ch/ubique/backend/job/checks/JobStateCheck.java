package ch.ubique.backend.job.checks;

import static ch.ubique.backend.job.model.JobRunState.SUCCESS_STATES;

import ch.ubique.backend.job.checks.cache.JobStateCheckEvaluationCache;
import ch.ubique.backend.job.checks.cache.JobStateCheckEvaluationData;
import ch.ubique.backend.job.model.JobClassAndId;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.properties.enums.StateCheckStrategy;
import ch.ubique.backend.monitoring.check.StatusCheck;
import ch.ubique.backend.monitoring.check.models.CheckFailed;
import ch.ubique.backend.monitoring.check.models.CheckLevel;
import ch.ubique.backend.monitoring.check.models.CheckStatus;

import java.util.*;
import java.util.stream.Stream;

public abstract class JobStateCheck implements StatusCheck {

    private final CheckLevel level;
    private final JobRunState stateToCheck;

    private final JobStateCheckEvaluationCache cache;

    protected JobStateCheck(
            final CheckLevel level,
            final JobRunState stateToCheck,
            final JobStateCheckEvaluationCache cache) {
        this.level = level;
        this.stateToCheck = stateToCheck;
        this.cache = cache;
    }

    @Override
    public CheckLevel level() {
        return level;
    }

    @Override
    public final CheckStatus check() {
        final var evaluation = cache.get();

        final var failedJobs = failedJobs(evaluation);
        if (!failedJobs.isEmpty()) {
            final var failedJobClassNames =
                    failedJobs.stream().map(JobClassAndId::canonicalClassName).toList();
            return new CheckFailed(
                    "Some jobs are in state " + stateToCheck + ".",
                    Map.of("jobs", failedJobClassNames));
        }

        return CheckStatus.CHECK_PASSED;
    }

    private List<JobClassAndId> failedJobs(final JobStateCheckEvaluationData evaluation) {
        final var failedJobs = new ArrayList<JobClassAndId>();
        failedJobs.addAll(failedJobsLatestState(evaluation).toList());
        failedJobs.addAll(failedJobsAnyFailure(evaluation).toList());
        failedJobs.addAll(failedJobsAnyFailureInInterval(evaluation).toList());
        failedJobs.addAll(failedJobsNoSuccessInInterval(evaluation).toList());
        return failedJobs;
    }

    /** Returns jobs failing the check for strategy CHECK_LATEST_SUCCESSFUL */
    private Stream<JobClassAndId> failedJobsLatestState(
            final JobStateCheckEvaluationData evaluation) {
        return evaluation.latestState().entrySet().stream()
                .filter(entry -> entry.getValue() == stateToCheck)
                .map(Map.Entry::getKey);
    }

    /** Returns jobs failing the check for strategy CHECK_ALL_SUCCESSFUL */
    private Stream<JobClassAndId> failedJobsAnyFailure(
            final JobStateCheckEvaluationData evaluation) {
        return evaluation.problemStates().entrySet().stream()
                .filter(entry -> entry.getValue().contains(stateToCheck))
                .map(Map.Entry::getKey);
    }

    /** Returns jobs failing the check for strategy CHECK_INTERVAL_ALL_SUCCESSFUL */
    private Stream<JobClassAndId> failedJobsAnyFailureInInterval(
            final JobStateCheckEvaluationData evaluation) {
        return evaluation.jobStateCheckStrategy().entrySet().stream()
                .filter(
                        entry ->
                                entry.getValue()
                                        == StateCheckStrategy.CHECK_INTERVAL_ALL_SUCCESSFUL)
                .map(Map.Entry::getKey)
                .filter(job -> evaluation.statesInInterval().get(job).contains(stateToCheck));
    }

    /** Returns jobs failing the check for strategy CHECK_INTERVAL_SOME_SUCCESSFUL */
    private Stream<JobClassAndId> failedJobsNoSuccessInInterval(
            final JobStateCheckEvaluationData evaluation) {
        return evaluation.jobStateCheckStrategy().entrySet().stream()
                .filter(
                        entry ->
                                entry.getValue()
                                        == StateCheckStrategy.CHECK_INTERVAL_SOME_SUCCESSFUL)
                .map(Map.Entry::getKey)
                .filter(
                        job -> {
                            final var jobRunStates = evaluation.statesInInterval().get(job);
                            return jobRunStates.contains(stateToCheck)
                                    && jobRunStates.stream().noneMatch(SUCCESS_STATES::contains);
                        });
    }
}
