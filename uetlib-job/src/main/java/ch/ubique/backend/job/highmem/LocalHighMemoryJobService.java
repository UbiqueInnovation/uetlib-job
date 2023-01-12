package ch.ubique.backend.job.highmem;

import ch.ubique.backend.job.BaseJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LocalHighMemoryJobService implements HighMemoryJobService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public void run(final BaseJob job) {
        logger.info("Running job by just calling the run method.");
        job.run(false);
    }
}
