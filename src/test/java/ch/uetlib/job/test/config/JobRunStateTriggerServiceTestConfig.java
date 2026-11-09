package ch.uetlib.job.test.config;

import ch.uetlib.job.annotation.RegisteredJobDefinitions;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.Set;

@SpringBootConfiguration
public class JobRunStateTriggerServiceTestConfig {

    @Bean
    RegisteredJobDefinitions registeredJobDefinitions() {
        return new RegisteredJobDefinitions(Set.of());
    }
}
