package ch.ubique.backend.job.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration
public class MysqlDBTestContainerConfig {
    @Bean
    @ServiceConnection
    MySQLContainer<?> mysqlDBContainer() {
        return new MySQLContainer<>(DockerImageName.parse("mysql:8.0.32"))
                .withConfigurationOverride("mysql_conf")
                .withReuse(true);
    }
}
