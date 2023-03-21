package ch.ubique.backend.job.cleanup;

import ch.ubique.backend.job.BaseJob;
import ch.ubique.backend.job.RegisteredJob;
import ch.ubique.backend.job.model.JobPropertyKey;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.storage.StorageClient;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;

@RegisteredJob
public class CleanupJob extends BaseJob {

    private final StorageClient storageClient;

    public CleanupJob(
            ApplicationContext context,
            @Qualifier("jobRunStorageClient") StorageClient storageClient) {
        super(context);
        this.storageClient = storageClient;
    }

    @Override
    protected JobRunState internalRun() throws Exception {

        for (var job : jobDataService.findJobs()) {
            // Get cleanup properties
            final var daysToKeep =
                    Integer.parseUnsignedInt(
                            jobDataService.getProperty(
                                    job.id(), JobPropertyKey.CLEANUP_DAYS_TO_KEEP));
            final var chunkSize =
                    Integer.parseUnsignedInt(
                            jobDataService.getProperty(
                                    job.id(), JobPropertyKey.CLEANUP_CHUNK_SIZE));

            final var cleanableRuns = jobDataService.findCleanableRuns(job.id(), daysToKeep);
            cleanableRuns.forEach(
                    run -> {
                        // Clean input files
                        final var jobRunInputs = jobDataService.findJobRunInputs(run.getId());
                        jobRunInputs.forEach(
                                input -> {
                                    final var path =
                                            "{jobName}/{runId}/{fileName}"
                                                    .replace("{jobName}", job.name())
                                                    .replace(
                                                            "{runId}",
                                                            String.format("Run_%06d", run.getId()))
                                                    .replace("{fileName}", input.getName() + ".gz");
                                    try {
                                        storageClient.delete(path);
                                    } catch (IOException e) {
                                        logger.warn(
                                                "Failed to delete job run input [jobId: {}; runId: {}; inputName: {}]",
                                                job.id(),
                                                run.getId(),
                                                input.getName());
                                    }
                                });
                    });

            for (var i = 0; i < cleanableRuns.size(); i += chunkSize) {
                // TODO: for-loops are out - any way to do this with streams? [TOPO-2358]
                jobDataService.deleteJobRunByIds(
                        cleanableRuns
                                .subList(i, Math.min(i + chunkSize, cleanableRuns.size()))
                                .stream()
                                .map(JobRun::getId)
                                .toList());
            }

            logger.info(
                    "Cleaned runs for job [jobId: {}; num_deletions: {}]",
                    job.id(),
                    cleanableRuns.size());
        }

        return JobRunState.DONE;
    }
}
