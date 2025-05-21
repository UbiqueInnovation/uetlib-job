package ch.ubique.backend.job.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@EnableScheduling
@Profile("!job-run")
public class UetlibJobWebSocketConfig implements WebSocketConfigurer {

    private final UetlibJobWebSocketHandler webSocketHandler;

    public UetlibJobWebSocketConfig(final UetlibJobWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(final WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketHandler, "/v1/status/live/logs").setAllowedOriginPatterns("*");
    }

    @Scheduled(fixedDelayString = "PT0.1S")
    public void sendLogs() {
        webSocketHandler.sendLog();
    }
}
