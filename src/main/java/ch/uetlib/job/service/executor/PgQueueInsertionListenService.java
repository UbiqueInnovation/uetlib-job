package ch.uetlib.job.service.executor;

import ch.uetlib.job.config.PgChannelListenerRegistryConfig;
import ch.uetlib.job.service.postgres.PgChannelListenerRegistry;

import jakarta.annotation.PostConstruct;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Collection;

/**
 * Claims newly queued runs right away on a Postgres {@code NOTIFY}; the scheduled poll remains the
 * fallback.
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
