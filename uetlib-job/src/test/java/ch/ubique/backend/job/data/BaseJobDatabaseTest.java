package ch.ubique.backend.job.data;

import ch.ubique.backend.job.config.JobDataServiceConfig;
import ch.ubique.backend.job.config.TestcontainerConfig;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureTestEntityManager;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.Transactional;

/**
 * Base test class for job service tests. Currently, only some queries are executed to check if the
 * sql syntax is valid on each database system.
 */
@Transactional
@AutoConfigureTestEntityManager
@EnableJpaRepositories(basePackages = "ch.ubique.backend.job.data.repository")
@EntityScan(basePackages = "ch.ubique.backend.job.model.entity")
@EnableAutoConfiguration
@SpringBootTest(
        classes = {
                JobDataServiceConfig.class,
                TestcontainerConfig.class,
        })
public abstract class BaseJobDatabaseTest {}
