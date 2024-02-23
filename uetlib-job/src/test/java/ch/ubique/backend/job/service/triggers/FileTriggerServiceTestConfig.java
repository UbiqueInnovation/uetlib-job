package ch.ubique.backend.job.service.triggers;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class FileTriggerServiceTestConfig {
    @Bean
    FileTriggerService fileTriggerService(final FileTriggerWatcher fileTriggerWatcher)
            throws IOException {
        return new FileTriggerService(fileTriggerWatcher);
    }
}
