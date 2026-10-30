package ch.ubique.uetlib.job.checks.cache;

import static ch.ubique.uetlib.job.model.properties.JobPropertyDefinitions.STATE_CHECK_INTERVAL;
import static ch.ubique.uetlib.job.model.properties.JobPropertyDefinitions.STATE_CHECK_STRATEGY;
import static java.util.Objects.requireNonNull;

import ch.ubique.uetlib.job.data.service.JobDataService;
import ch.ubique.uetlib.job.model.JobClassAndId;
import ch.ubique.uetlib.job.model.JobRun;
import ch.ubique.uetlib.job.model.JobRunState;
import ch.ubique.uetlib.job.model.properties.enums.StateCheckStrategy;
import ch.ubique.uetlib.job.service.JobService;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!job-run")
public class JobStateCheckEvaluationCache {

    private final JobDataService jobDataService;
    private final JobService jobService;

    private @Nullable JobStateCheckEvaluationData latestEvaluation;
    private @Nullable Instant latestEvaluationTime;

    private static final Duration CACHE_DURATION = Duration.ofSeconds(10);

    JobStateCheckEvaluationCache(final JobDataService jobDataService, final JobService jobService) {
        this.jobDataService = jobDataService;
        this.jobService = jobService;
    }

    public synchronized JobStateCheckEvaluationData get() {
        if (latestEvaluation == null
                || latestEvaluationTime == null
                || latestEvaluationTime.isBefore(Instant.now().minus(CACHE_DURATION))) {
            latestEvaluation = evaluate();
            latestEvaluationTime = Instant.now();
        }
        return latestEvaluation;
    }

    public void purge() {
        latestEvaluation = null;
        latestEvaluationTime = null;
    }

    JobStateCheckEvaluationData evaluate() {
        final var jobStateCheckStrategy =
                jobService.getEnabledJobClassesWithId().stream()
                        .collect(Collectors.toMap(job -> job, this::stateCheckStrategy));

        final var latestState =
                findLatestRunStates(jobsWithCheckLatestSuccessfulStrategy(jobStateCheckStrategy));
        final var problemRunStates =
                findProblemRunStates(jobsWithCheckAllSuccessfulStrategy(jobStateCheckStrategy));
        final var statesInInterval =
                findRunStatesInInterval(jobsWithCheckIntervalStrategy(jobStateCheckStrategy));
        return new JobStateCheckEvaluationData(
                jobStateCheckStrategy, latestState, problemRunStates, statesInInterval);
    }

    private Map<JobClassAndId, JobRunState> findLatestRunStates(
            final Collection<JobClassAndId> jobs) {
        if (jobs.isEmpty()) {
            return Collections.emptyMap();
        }
        final var jobById = jobs.stream().collect(Collectors.toMap(JobClassAndId::id, job -> job));

        return jobDataService
                .findLatestFinishedJobRuns(jobs.stream().map(JobClassAndId::id).toList())
                .stream()
                .collect(
                        Collectors.toMap(
                                run -> requireNonNull(jobById.get(run.jobId())),
                                JobRun::latestState));
    }

    private Map<JobClassAndId, Set<JobRunState>> findProblemRunStates(
            final Collection<JobClassAndId> jobs) {
        if (jobs.isEmpty()) {
            return Collections.emptyMap();
        }
        final var jobById = jobs.stream().collect(Collectors.toMap(JobClassAndId::id, job -> job));
        final var jobIds = jobs.stream().map(JobClassAndId::id).toList();

        return jobDataService.findJobRunStates(jobIds).entrySet().stream()
                .collect(
                        Collectors.toMap(
                                entry -> requireNonNull(jobById.get(entry.getKey())),
                                entry -> onlyProblemRunStates(entry.getValue())));
    }

    private Map<JobClassAndId, Set<JobRunState>> findRunStatesInInterval(
            final Collection<JobClassAndId> jobs) {
        if (jobs.isEmpty()) {
            return Collections.emptyMap();
        }
        final var now = Instant.now();
        final var jobById = jobs.stream().collect(Collectors.toMap(JobClassAndId::id, job -> job));
        final var checkIntervalStart =
                jobs.stream()
                        .collect(
                                Collectors.toMap(
                                        JobClassAndId::id, job -> checkIntervalStart(job, now)));

        return jobDataService.findJobRunStatesSince(checkIntervalStart).entrySet().stream()
                .collect(
                        Collectors.toMap(
                                entry -> requireNonNull(jobById.get(entry.getKey())),
                                Map.Entry::getValue));
    }

    private List<JobClassAndId> jobsWithCheckLatestSuccessfulStrategy(
            final Map<JobClassAndId, StateCheckStrategy> jobStateCheckStrategy) {
        return jobStateCheckStrategy.entrySet().stream()
                .filter(entry -> entry.getValue() == StateCheckStrategy.CHECK_LATEST_SUCCESSFUL)
                .map(Map.Entry::getKey)
                .toList();
    }

    private List<JobClassAndId> jobsWithCheckAllSuccessfulStrategy(
            final Map<JobClassAndId, StateCheckStrategy> jobStateCheckStrategy) {
        return jobStateCheckStrategy.entrySet().stream()
                .filter(entry -> entry.getValue() == StateCheckStrategy.CHECK_ALL_SUCCESSFUL)
                .map(Map.Entry::getKey)
                .toList();
    }

    private List<JobClassAndId> jobsWithCheckIntervalStrategy(
            final Map<JobClassAndId, StateCheckStrategy> jobStateCheckStrategy) {
        final var checkIntervalStrategies =
                Set.of(
                        StateCheckStrategy.CHECK_INTERVAL_SOME_SUCCESSFUL,
                        StateCheckStrategy.CHECK_INTERVAL_ALL_SUCCESSFUL);
        return jobStateCheckStrategy.entrySet().stream()
                .filter(entry -> checkIntervalStrategies.contains(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
    }

    private Instant checkIntervalStart(final JobClassAndId job, final Instant now) {
        final var checkInterval =
                jobService
                        .getJobClassMaybe(job)
                        .map(jobService::instantiateJob)
                        .map(jobService::getProperties)
                        .map(p -> p.getValue(STATE_CHECK_INTERVAL))
                        .orElse(STATE_CHECK_INTERVAL.defaultValue().orElseThrow());
        return now.minus(checkInterval);
    }

    private StateCheckStrategy stateCheckStrategy(final JobClassAndId job) {
        return jobService
                .getJobClassMaybe(job)
                .map(jobService::instantiateJob)
                .map(jobService::getProperties)
                .map(p -> p.getValue(STATE_CHECK_STRATEGY))
                .orElse(STATE_CHECK_STRATEGY.defaultValue().orElseThrow());
    }

    private static Set<JobRunState> onlyProblemRunStates(final Set<JobRunState> runStates) {
        return runStates.stream()
                .filter(JobRunState.PROBLEM_STATES::contains)
                .collect(Collectors.toSet());
    }
}
