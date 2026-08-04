package ch.ubique.backend.job.service.executor;

import ch.ubique.backend.job.config.PgChannelListenerRegistryConfig;
import ch.ubique.backend.job.service.postgres.PgChannelListenerRegistry;

import jakarta.annotation.PostConstruct;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Collection;

/**
 * Listens on the PostgreSQL {@code new_job_queued_run} channel, which is notified by a database
 * trigger whenever a row is inserted into the job run queue, and reacts by immediately checking
 * whether the newly queued run can be executed on this runner.
 *
 * <p>This is only a fast path, available on PostgreSQL: {@link JobRunQueueService}'s scheduled
 * checks keep running regardless and act as the fallback that catches everything else (other
 * database types, missed/coalesced notifications, runs that only became runnable later e.g. due to
 * freed up capacity).
 */
@Service
@Profile("!job-run")
public class PgQueueInsertionListenService {

    static final String CHANNEL = "new_job_queued_run";

    private static final Logger logger =
            LoggerFactory.getLogger(PgQueueInsertionListenService.class);

    private final PgChannelListenerRegistry channelListenerRegistry;
    private final JobRunQueueService jobRunQueueService;

    public PgQueueInsertionListenService(
            @Qualifier(PgChannelListenerRegistryConfig.JOB_QUEUE_REGISTRY)
                    final PgChannelListenerRegistry channelListenerRegistry,
            final JobRunQueueService jobRunQueueService) {
        this.channelListenerRegistry = channelListenerRegistry;
        this.jobRunQueueService = jobRunQueueService;
    }

    @PostConstruct
    void init() {
        if (!channelListenerRegistry.isPostgres()) {
            logger.debug(
                    "Not listening on channel {}, datasource is not PostgreSQL; relying on the"
                            + " scheduled queue checks only.",
                    CHANNEL);
            return;
        }
        channelListenerRegistry.listen(CHANNEL, this::onJobRunQueued);
    }

    private void onJobRunQueued(final Collection<String> payloads) {
        logger.debug(
                "Received notification on channel {} for job id(s) {}, checking queue for"
                        + " runnable runs.",
                CHANNEL,
                payloads);
        jobRunQueueService.checkForRunnable(
                payloads.stream()
                        .map(p -> p.split("\\|"))
                        .map(p -> Pair.of(Integer.parseInt(p[0]), Integer.parseInt(p[1])))
                        .toList());
    }
}
