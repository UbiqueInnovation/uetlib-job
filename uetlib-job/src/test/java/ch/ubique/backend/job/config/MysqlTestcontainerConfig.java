package ch.ubique.backend.job.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@Profile("mysql")
@TestConfiguration
public class MysqlTestcontainerConfig extends BaseDatabaseTestcontainerConfig {

    @Bean
    @ServiceConnection
    MySQLContainer<?> mysqlContainer() {
        return new MySQLContainer<>(DockerImageName.parse("mysql:8.3"));
    }
}
