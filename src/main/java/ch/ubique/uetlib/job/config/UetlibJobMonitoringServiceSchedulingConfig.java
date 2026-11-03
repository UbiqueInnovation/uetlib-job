package ch.ubique.uetlib.job.config;

import ch.ubique.uetlib.monitoring.service.MonitoringService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableScheduling
@Profile("!job-run")
@ConditionalOnProperty("ch.ubique.backend.job.interface.heartbeat.endpoint")
public class UetlibJobMonitoringServiceSchedulingConfig {

    private final MonitoringService monitoringService;

    public UetlibJobMonitoringServiceSchedulingConfig(final MonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.MINUTES)
    public void heartBeat() {
        monitoringService.heartbeat();
    }
}
