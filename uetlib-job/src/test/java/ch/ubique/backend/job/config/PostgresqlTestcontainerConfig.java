package ch.ubique.backend.job.config;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
<<<<<<< HEAD:backend-jvm-lib/backend-jvm-lib-job/src/test/java/ch/ubique/backend/job/config/PostgresqlTestcontainerConfig.java
import org.springframework.context.annotation.Configuration;
=======
import org.springframework.context.annotation.Import;
>>>>>>> 98e48143 (Fix tests but all failing):backend-jvm-lib/backend-jvm-lib-job/src/test/java/ch/ubique/backend/job/config/PostgresqlTestContainerConfig.java
import org.springframework.context.annotation.Profile;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

<<<<<<< HEAD:backend-jvm-lib/backend-jvm-lib-job/src/test/java/ch/ubique/backend/job/config/PostgresqlTestcontainerConfig.java
@Profile("postgres")
@Configuration
public class PostgresqlTestcontainerConfig {
=======
@Profile("!mysql")
@TestConfiguration
public class PostgresqlTestContainerConfig {
>>>>>>> 98e48143 (Fix tests but all failing):backend-jvm-lib/backend-jvm-lib-job/src/test/java/ch/ubique/backend/job/config/PostgresqlTestContainerConfig.java
    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresDBContainer() {
        //noinspection resource
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16")).withReuse(true);
    }
}
