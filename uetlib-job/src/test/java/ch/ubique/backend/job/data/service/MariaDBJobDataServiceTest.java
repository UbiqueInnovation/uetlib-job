package ch.ubique.backend.job.data.service;

import ch.ubique.backend.job.config.*;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

@EnableAutoConfiguration
@SpringBootTest(
        classes = {
            MariaDBTestContainerConfig.class,
            MariaDBFlywayConfig.class,
            JobDataServiceConfig.class
        })
public class MariaDBJobDataServiceTest extends JobDataServiceTest {}
