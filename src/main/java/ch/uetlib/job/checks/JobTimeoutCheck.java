package ch.uetlib.job.checks;

import static ch.uetlib.job.model.properties.JobPropertyDefinitions.RUN_TIME_LIMIT;

import ch.uetlib.job.data.service.JobDataService;
import ch.uetlib.job.model.JobClassAndId;
import ch.uetlib.job.model.JobRun;
import ch.uetlib.job.model.JobRunState;
import ch.uetlib.job.service.JobService;
import ch.uetlib.monitoring.check.StatusCheck;
import ch.uetlib.monitoring.check.models.CheckFailed;
import ch.uetlib.monitoring.check.models.CheckLevel;
import ch.uetlib.monitoring.check.models.CheckStatus;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Profile("!job-run")
public class JobTimeoutCheck implements StatusCheck {

    private final CheckLevel level;
    private final JobService jobService;
    private final JobDataService jobDataService;

    public JobTimeoutCheck(
            @Value("${ch.ubique.backend.job.check.timeout:ERROR}") final CheckLevel level,
            final JobService jobService,
            final JobDataService jobDataService) {
        this.level = level;
        this.jobService = jobService;
        this.jobDataService = jobDataService;
    }

    @Override
    public CheckLevel level() {
        return level;
    }

    @Override
    public CheckStatus check() {

        final var errors = failedJobsRunningForTooLong(jobService.getEnabledJobClassesWithId());

        if (!errors.isEmpty()) {
            return new CheckFailed("Some jobs exceeded the run time limit.", errors);
        }

        return CheckStatus.CHECK_PASSED;
    }

    private List<Map<String, Object>> failedJobsRunningForTooLong(
            final Collection<JobClassAndId> jobs) {
        final var jobsById = jobs.stream().collect(Collectors.toMap(JobClassAndId::id, job -> job));
        return jobDataService.findJobRunsInState(jobsById.keySet(), JobRunState.BUSY).stream()
                .flatMap(
                        busyRun -> {
                            final var job = jobsById.get(busyRun.jobId());
                            if (job == null) {
                                return java.util.stream.Stream.empty();
                            }
                            return runtimeLimitExceededError(job, busyRun).stream();
                        })
                .toList();
    }

    private Optional<Map<String, Object>> runtimeLimitExceededError(
            final JobClassAndId jobClassAndId, final JobRun jobRun) {
        final var runtimeLimit = runTimeLimit(jobClassAndId);

        if (jobRun.runDuration().compareTo(runtimeLimit) <= 0) {
            return Optional.empty();
        }

        return Optional.of(
                Map.of(
                        "job",
                        jobClassAndId.canonicalClassName(),
                        "run",
                        jobRun.id(),
                        "limit",
                        runtimeLimit,
                        "duration",
                        jobRun.runDuration()));
    }

    private Duration runTimeLimit(final JobClassAndId job) {
        return jobService
                .getJobClassMaybe(job)
                .map(jobService::instantiateJob)
                .map(jobService::getProperties)
                .map(p -> p.getValue(RUN_TIME_LIMIT))
                .orElse(RUN_TIME_LIMIT.defaultValue().orElseThrow());
    }
}
