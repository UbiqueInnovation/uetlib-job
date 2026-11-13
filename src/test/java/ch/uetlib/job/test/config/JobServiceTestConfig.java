package ch.uetlib.job.test.config;

import static org.mockito.Mockito.mock;

import ch.uetlib.job.annotation.EnableJobs;
import ch.uetlib.job.annotation.RegisteredJobDefinitions;
import ch.uetlib.job.config.HibernateJsonConfiguration;
import ch.uetlib.job.config.PgChannelListenerRegistryConfig;
import ch.uetlib.job.config.UetlibJobPgListenDataSourceProviderConfig;
import ch.uetlib.job.config.UetlibJobQueueDataSourceProviderConfig;
import ch.uetlib.job.data.service.JobDataService;
import ch.uetlib.job.service.core.ClassLoaderService;
import ch.uetlib.job.service.register.JobRegistrationService;
import ch.uetlib.job.storage.JobRunStorage;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.zalando.logbook.Logbook;

import java.util.List;

import javax.sql.DataSource;

@SpringBootConfiguration
@ImportAutoConfiguration({
    UetlibJobPgListenDataSourceProviderConfig.class,
    UetlibJobQueueDataSourceProviderConfig.class
})
@Import({PgChannelListenerRegistryConfig.class, HibernateJsonConfiguration.class})
@ComponentScan({"ch.uetlib.job.data.service", "ch.uetlib.job.service"})
@EnableJobs("ch.uetlib.job.testjob")
@EnableJpaRepositories(basePackages = "ch.uetlib.job.data.repository")
@EntityScan(basePackages = "ch.uetlib.job.model.entity")
public class JobServiceTestConfig {

    private final DataSource dataSource;

    @Qualifier("databaseSpecificInitializationScripts")
    private final List<ClassPathResource> databaseSpecificInitializationScripts;

    public JobServiceTestConfig(
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

    /** bean registeredJobDefinitions is registered programmatically in RegisteredJobRegistrar */
    @Bean
    JobRegistrationService jobRegistrationService(
            final JobDataService jobDataService,
            final ClassLoaderService classLoaderService,
            @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
                    final RegisteredJobDefinitions registeredJobDefinitions) {
        return new JobRegistrationService(
                jobDataService,
                classLoaderService,
                mock(Environment.class),
                registeredJobDefinitions);
    }

    @Bean
    CloseableHttpClient httpClient() {
        return HttpClients.createSystem();
    }

    @Bean
    JobRunStorage jobRunStorage() {
        return mock(JobRunStorage.class);
    }

    @Bean
    Logbook logbook() {
        return Logbook.create();
    }
}
