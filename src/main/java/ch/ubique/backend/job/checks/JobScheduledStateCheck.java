package ch.ubique.backend.job.checks;

import static ch.ubique.backend.job.model.properties.JobPropertyDefinitions.MAX_DURATION_IN_SCHEDULED_STATE;

import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.JobClassAndId;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.service.JobService;
import ch.ubique.backend.monitoring.check.StatusCheck;
import ch.ubique.backend.monitoring.check.models.CheckFailed;
import ch.ubique.backend.monitoring.check.models.CheckLevel;
import ch.ubique.backend.monitoring.check.models.CheckStatus;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Profile("!job-run")
public class JobScheduledStateCheck implements StatusCheck {

    private final CheckLevel level;
    private final JobService jobService;
    private final JobDataService jobDataService;

    public JobScheduledStateCheck(
            @Value("${ch.ubique.backend.job.check.scheduled-state:ERROR}") final CheckLevel level,
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
        final var scheduledStateForTooLong =
                failedJobsScheduledForTooLong(jobService.getEnabledJobClassesWithId());

        if (!scheduledStateForTooLong.isEmpty()) {
            return new CheckFailed(
                    "The following jobs have been in the "
                            + JobRunState.SCHEDULED
                            + " for too long.",
                    scheduledStateForTooLong.stream()
                            .map(JobClassAndId::canonicalClassName)
                            .toList());
        }

        return CheckStatus.CHECK_PASSED;
    }

    private List<JobClassAndId> failedJobsScheduledForTooLong(
            final Collection<JobClassAndId> jobs) {
        final var jobsById = jobs.stream().collect(Collectors.toMap(JobClassAndId::id, job -> job));
        return jobDataService.findJobRunsInState(jobsById.keySet(), JobRunState.SCHEDULED).stream()
                .filter(
                        scheduledRun -> {
                            final var job = jobsById.get(scheduledRun.jobId());
                            return job != null
                                    && exceedsMaxDurationInScheduledState(job, scheduledRun);
                        })
                .map(jobRun -> jobsById.get(jobRun.jobId()))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    private boolean exceedsMaxDurationInScheduledState(
            final JobClassAndId jobClassAndId, final JobRun scheduledRun) {
        return scheduledRun.runDuration().compareTo(maxDurationInScheduledState(jobClassAndId)) > 0;
    }

    private Duration maxDurationInScheduledState(final JobClassAndId job) {
        return jobService
                .getJobClassMaybe(job)
                .map(jobService::instantiateJob)
                .map(jobService::getProperties)
                .map(p -> p.getValue(MAX_DURATION_IN_SCHEDULED_STATE))
                .orElse(MAX_DURATION_IN_SCHEDULED_STATE.defaultValue().orElseThrow());
    }
}
