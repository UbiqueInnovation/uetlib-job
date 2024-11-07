package ch.ubique.backend.job.test.config;

import ch.ubique.backend.job.service.triggers.FileTriggerService;
import ch.ubique.backend.job.service.triggers.FileTriggerWatcher;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;

@SpringBootConfiguration
public class FileTriggerServiceTestConfig {

    @Bean
    FileTriggerService fileTriggerService(final FileTriggerWatcher fileTriggerWatcher) {
        return new FileTriggerService(fileTriggerWatcher);
    }
}
