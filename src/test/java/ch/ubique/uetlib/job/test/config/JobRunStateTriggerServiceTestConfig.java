package ch.ubique.uetlib.job.test.config;

import ch.ubique.uetlib.job.annotation.RegisteredJobDefinitions;
import java.util.Set;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;

@SpringBootConfiguration
public class JobRunStateTriggerServiceTestConfig {

    @Bean
    RegisteredJobDefinitions registeredJobDefinitions() {
        return new RegisteredJobDefinitions(Set.of());
    }
}
