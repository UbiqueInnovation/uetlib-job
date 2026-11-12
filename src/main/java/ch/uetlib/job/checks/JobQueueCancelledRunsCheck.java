package ch.uetlib.job.checks;

import ch.uetlib.core.utils.MapCollectors;
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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Warns while any queue row is {@link JobRunQueueState#CANCELLED}: a run nothing claimed in time.
 */
@Service
@Profile("!job-run")
public class JobQueueCancelledRunsCheck implements StatusCheck {

    private static final String UNRESOLVED_JOB_NAME = "Job name not found (may have been deleted)";

    private final CheckLevel level;
    private final JobDataService jobDataService;
    private final JobService jobService;

    public JobQueueCancelledRunsCheck(
            @Value("${ch.uetlib.job.check.cancelled-queued-runs:WARNING}") final CheckLevel level,
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

        // LinkedHashMap throughout: monitoring output must not reorder between checks.
        final var countByJobId =
                cancelledRuns.stream()
                        .collect(
                                Collectors.groupingBy(
                                        JobQueuedRun::jobId,
                                        LinkedHashMap::new,
                                        Collectors.counting()));
        final var jobsById =
                countByJobId.keySet().stream()
                        .map(jobService::findJobById)
                        .flatMap(Optional::stream)
                        .collect(MapCollectors.toMap(JobClassAndId::id));

        final var countByJobName = new LinkedHashMap<String, Long>();
        countByJobId.forEach(
                (jobId, count) ->
                        countByJobName.put(
                                Optional.ofNullable(jobsById.get(jobId))
                                        .map(JobClassAndId::name)
                                        .orElse(UNRESOLVED_JOB_NAME + " (id=" + jobId + ")"),
                                count));

        return new CheckFailed(
                "Some queued runs could not be claimed in time and are cancelled.",
                Map.ofEntries(
                        Pair.of("count", cancelledRuns.size()),
                        Pair.of(
                                "jobs",
                                countByJobId.keySet().stream()
                                        .map(jobsById::get)
                                        .filter(Objects::nonNull)
                                        .map(JobClassAndId::canonicalClassName)
                                        .toList()),
                        Pair.of("jobsCount", countByJobName)));
    }
}
