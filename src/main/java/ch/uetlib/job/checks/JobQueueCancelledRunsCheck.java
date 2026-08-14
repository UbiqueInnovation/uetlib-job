package ch.uetlib.job.checks;

import ch.uetlib.job.data.service.JobDataService;
import ch.uetlib.job.model.JobClassAndId;
import ch.uetlib.job.model.JobQueuedRun;
import ch.uetlib.job.model.queue.JobRunQueueState;
import ch.uetlib.job.service.JobService;
import ch.uetlib.monitoring.check.StatusCheck;
import ch.uetlib.monitoring.check.models.CheckFailed;
import ch.uetlib.monitoring.check.models.CheckLevel;
import ch.uetlib.monitoring.check.models.CheckStatus;

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * Warns when any {@code t_job_run_queue} row has been marked {@link JobRunQueueState#CANCELLED} - a
 * run nothing could claim in time (no currently running instance matched its version or taints; see
 * {@code QUEUE_TIMEOUT}, {@code SCHEDULING.md} §2/§7). Distinct from {@link
 * JobCancelledStateCheck}: that one is about a {@code JobRun}'s own {@code CANCELLED} state - an
 * execution that was stopped after starting - whereas this is about a queue row that was never
 * picked up at all.
 */
@Service
@Profile("!job-run")
public class JobQueueCancelledRunsCheck implements StatusCheck {

    private final CheckLevel level;
    private final JobDataService jobDataService;
    private final JobService jobService;

    public JobQueueCancelledRunsCheck(
            @Value("${ch.uetlib.job.check.cancelled-queued-runs:WARNING}")
                    final CheckLevel level,
            final JobDataService jobDataService,
            final JobService jobService) {
        this.level = level;
        this.jobDataService = jobDataService;
        this.jobService = jobService;
    }

    @Override
    public CheckLevel level() {
        return level;
    }

    @Override
    public CheckStatus check() {
        final var cancelledRuns = jobDataService.findQueuedRunsByState(JobRunQueueState.CANCELLED);
        if (cancelledRuns.isEmpty()) {
            return CheckStatus.CHECK_PASSED;
        }

        final var affectedJobs =
                cancelledRuns.stream()
                        .map(JobQueuedRun::jobId)
                        .distinct()
                        .map(jobService::findJobById)
                        .flatMap(Optional::stream)
                        .toList();
        final var affectedJobsCountByJob =
                cancelledRuns.stream()
                        .collect(
                                java.util.stream.Collectors.groupingBy(
                                        run ->
                                                affectedJobs.stream()
                                                        .filter(job -> job.id() == run.jobId())
                                                        .findFirst()
                                                        .map(JobClassAndId::name)
                                                        .orElse(
                                                                "Job name not found (may have been deleted)"),
                                        java.util.stream.Collectors.counting()));
        return new CheckFailed(
                "Some queued runs could not be claimed in time and are cancelled.",
                Map.ofEntries(
                        Pair.of("count", cancelledRuns.size()),
                        Pair.of(
                                "jobs",
                                affectedJobs.stream()
                                        .map(JobClassAndId::canonicalClassName)
                                        .toList()),
                        Pair.of("jobsCount", affectedJobsCountByJob)));
    }
}
