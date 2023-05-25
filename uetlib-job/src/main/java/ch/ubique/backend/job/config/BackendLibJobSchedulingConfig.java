package ch.ubique.backend.job.config;

import ch.ubique.backend.job.heath.SchedulerCheckService;
import ch.ubique.backend.monitoring.service.MonitoringService;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableScheduling
public class BackendLibJobSchedulingConfig {

    private final MonitoringService monitoringService;
    private final SchedulerCheckService schedulerCheckService;

    public BackendLibJobSchedulingConfig(
            final MonitoringService monitoringService,
            final SchedulerCheckService schedulerCheckService) {
        this.monitoringService = monitoringService;
        this.schedulerCheckService = schedulerCheckService;
    }

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.MINUTES)
    public void heartBeat() {
        monitoringService.heartbeat();
    }

    @Scheduled(fixedRate = 15, timeUnit = TimeUnit.MINUTES)
    public void checkScheduler() {
        schedulerCheckService.checkSchedulerTriggers();
    }
}
