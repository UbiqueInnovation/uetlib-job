package ch.ubique.backend.job.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.utility.DockerImageName;

@Profile("mariadb")
@TestConfiguration
public class MariaDBTestcontainerConfig {
    @Bean
    @ServiceConnection
    MariaDBContainer<?> mariaDBContainer() {
        //noinspection resource
        return new MariaDBContainer<>(DockerImageName.parse("mariadb:10.6.5"))
                .withConfigurationOverride("mysql_conf");
    }
}
