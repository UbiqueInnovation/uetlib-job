package ch.ubique.backend.job.config;

import ch.ubique.backend.monitoring.service.MonitoringService;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
public class BackendLibJobSchedulingConfig {

    private final MonitoringService monitoringService;

    public BackendLibJobSchedulingConfig(final MonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.MINUTES)
    public void heartBeat() {
        monitoringService.heartbeat();
    }
}
