package ch.ubique.backend.job.data.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

public abstract class AbstractJobDataService implements JobDataService {

    @SuppressWarnings("unused")
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Value("${job.startup.cleanUpBusyRuns:true}")
    private boolean cleanUpBusyRuns;

    @Override
    public boolean doCleanUpBusyRuns() {
        return cleanUpBusyRuns;
    }
}
