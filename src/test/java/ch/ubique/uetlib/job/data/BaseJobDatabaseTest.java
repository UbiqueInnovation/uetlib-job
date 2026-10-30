package ch.ubique.uetlib.job.data;

import ch.ubique.uetlib.job.test.config.JobServiceConfig;
import ch.ubique.uetlib.job.test.config.TestcontainerConfig;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jpa.test.autoconfigure.AutoConfigureTestEntityManager;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Base test class for job service tests. Currently, only some queries are executed to check if the
 * SQL syntax is valid on each database system.
 */
// Comment-in to run these tests from within IntelliJ. These profiles are usually set for the tests
// in the pom.xml
// @ActiveProfiles({"test", "no-security", "postgres"})
@SpringBootTest(
        classes = {
            JobServiceConfig.class,
            TestcontainerConfig.class,
        })
@Transactional
@AutoConfigureTestEntityManager
@EnableAutoConfiguration
public abstract class BaseJobDatabaseTest {}
