package ch.ubique.backend.job.test.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({
    PostgresqlTestcontainerConfig.class,
    MysqlTestcontainerConfig.class,
    MariaDBTestcontainerConfig.class
})
public class TestcontainerConfig extends BaseDatabaseTestcontainerConfig {}
