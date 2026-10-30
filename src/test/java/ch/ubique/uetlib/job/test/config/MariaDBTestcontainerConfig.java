package ch.ubique.uetlib.job.test.config;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.testcontainers.mariadb.MariaDBContainer;
import org.testcontainers.utility.DockerImageName;

@Profile("mariadb")
@Configuration
public class MariaDBTestcontainerConfig {

    @Bean
    @ServiceConnection
    MariaDBContainer mariaDBContainer() {
        return new MariaDBContainer(DockerImageName.parse("mariadb:13.0"));
    }
}
