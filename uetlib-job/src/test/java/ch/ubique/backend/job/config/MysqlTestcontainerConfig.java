package ch.ubique.backend.job.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@Profile("mysql")
@TestConfiguration
public class MysqlTestcontainerConfig extends BaseDatabaseTestConfig {

    @Override
    String databaseSpecificProfileName() {
        return "mysql";
    }

    @Bean
    @ServiceConnection
    MySQLContainer<?> mysqlContainer() {
        //noinspection resource
        return new MySQLContainer<>(DockerImageName.parse("mysql:8"))
                .withConfigurationOverride("mysql_conf");
    }
}
