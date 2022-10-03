package ch.ubique.backend.job.service;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class WebSocketEventListener implements ApplicationListener<SessionDisconnectEvent> {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketEventListener.class);

    @Override
    public void onApplicationEvent(final @NotNull SessionDisconnectEvent event) {
        logger.warn("SessionDisconnectEvent = {}, sessionId = {}", event, event.getSessionId());
    }
}
