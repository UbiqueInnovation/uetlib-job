package ch.ubique.backend.job.config;

import ch.ubique.backend.job.service.JobService;
import ch.ubique.backend.monitoring.service.MonitoringService;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableScheduling
@Profile("!job-run")
public class BackendLibJobSchedulingConfig {

    private final MonitoringService monitoringService;
    private final JobService jobService;

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    public BackendLibJobSchedulingConfig(
            final MonitoringService monitoringService, final JobService jobService) {
        this.monitoringService = monitoringService;
        this.jobService = jobService;
    }

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.MINUTES)
    public void heartBeat() {
        monitoringService.heartbeat();
    }

    @Scheduled(fixedRate = Integer.MAX_VALUE)
    public void jobServicePostConstruct() {
        jobService.registerAllJobs();
        jobService.setAbortedRunsToUnfinished();
    }
}
