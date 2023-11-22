package ch.ubique.backend.job.data.service;

import ch.ubique.backend.job.config.JobDataServiceConfig;
import ch.ubique.backend.job.config.MariaDBFlywayConfig;
import ch.ubique.backend.job.config.MariaDBTestContainerConfig;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@EnableAutoConfiguration
@Import({MariaDBTestContainerConfig.class, MariaDBFlywayConfig.class, JobDataServiceConfig.class})
public class MariaDBJobDataServiceTest extends JobDataServiceTest {}
