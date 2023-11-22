package ch.ubique.backend.job.data.service;

import ch.ubique.backend.job.config.JobDataServiceConfig;
import ch.ubique.backend.job.config.PostgresqlFlywayConfig;
import ch.ubique.backend.job.config.PostgresqlTestContainerConfig;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@EnableAutoConfiguration
@Import({PostgresqlTestContainerConfig.class, PostgresqlFlywayConfig.class, JobDataServiceConfig.class})
public class PostgresqlJobDataServiceTest extends JobDataServiceTest {}
