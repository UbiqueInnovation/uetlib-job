package ch.ubique.backend.job.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableWebSocket
@EnableScheduling
public class JobWebSocketConfig implements WebSocketConfigurer {

    private final JobWebSocketHandler jobWebSocketHandler;

    public JobWebSocketConfig(final JobWebSocketHandler jobWebSocketHandler) {
        this.jobWebSocketHandler = jobWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(final WebSocketHandlerRegistry registry) {
        registry.addHandler(jobWebSocketHandler, "/v1/live/logs").setAllowedOriginPatterns("*");
    }

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.SECONDS)
    public void sendLogs() {
        jobWebSocketHandler.sendLog();
    }
}
