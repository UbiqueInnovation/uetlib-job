package ch.ubique.backend.job.config;

import ch.ubique.backend.job.annotation.EnableJobs;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.List;

import javax.sql.DataSource;

@SpringBootConfiguration
@ComponentScan({
    "ch.ubique.backend.job.data.service",
    "ch.ubique.backend.job.config",
    "ch.ubique.backend.job.service"
})
@EnableJobs("ch.ubique.backend.job.testjob")
public class JobServiceConfig {

    private final DataSource dataSource;

    @Qualifier("databaseSpecificInitializationScripts")
    private final List<ClassPathResource> databaseSpecificInitializationScripts;

    public JobServiceConfig(
            final DataSource dataSource,
            final List<ClassPathResource> databaseSpecificInitializationScripts) {
        this.dataSource = dataSource;
        this.databaseSpecificInitializationScripts = databaseSpecificInitializationScripts;
    }

    @Bean
    @DependsOnDatabaseInitialization
    ResourceDatabasePopulator resourceDatabasePopulator() {
        final var resourceDatabasePopulator = new ResourceDatabasePopulator();
        databaseSpecificInitializationScripts.forEach(resourceDatabasePopulator::addScript);
        resourceDatabasePopulator.execute(dataSource);
        return resourceDatabasePopulator;
    }

    @Bean
    TaskScheduler taskScheduler() {
        return new ThreadPoolTaskScheduler();
    }
}
