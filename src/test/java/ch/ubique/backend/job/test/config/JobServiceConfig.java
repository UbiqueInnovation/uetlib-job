package ch.ubique.backend.job.test.config;

import static org.mockito.Mockito.mock;

import ch.ubique.backend.job.annotation.EnableJobs;
import ch.ubique.backend.job.annotation.RegisteredJobDefinitions;
import ch.ubique.backend.job.config.HibernateJsonConfiguration;
import ch.ubique.backend.job.config.PgChannelListenerRegistryConfig;
import ch.ubique.backend.job.config.UetlibJobPgListenDataSourceProviderConfig;
import ch.ubique.backend.job.config.UetlibJobQueueDataSourceProviderConfig;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.service.core.ClassLoaderService;
import ch.ubique.backend.job.service.register.JobRegistrationService;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.ApplicationContext;
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
@Import({
    UetlibJobPgListenDataSourceProviderConfig.class,
    UetlibJobQueueDataSourceProviderConfig.class,
    PgChannelListenerRegistryConfig.class,
    HibernateJsonConfiguration.class
})
@ComponentScan({"ch.ubique.backend.job.data.service", "ch.ubique.backend.job.service"})
@EnableJobs("ch.ubique.backend.job.testjob")
@EnableJpaRepositories(basePackages = "ch.ubique.backend.job.data.repository")
@EntityScan(basePackages = "ch.ubique.backend.job.model.entity")
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

    /** bean registeredJobDefinitions is registered programmatically in RegisteredJobRegistrar */
    @Bean
    JobRegistrationService jobRegistrationService(
            final JobDataService jobDataService,
            final ClassLoaderService classLoaderService,
            final ApplicationContext applicationContext,
            @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
                    final RegisteredJobDefinitions registeredJobDefinitions) {
        return new JobRegistrationService(
                jobDataService,
                classLoaderService,
                applicationContext,
                mock(Environment.class),
                registeredJobDefinitions);
    }

    @Bean
    CloseableHttpClient httpClient() {
        return HttpClients.createSystem();
    }

    @Bean
    Logbook logbook() {
        return Logbook.create();
    }
}
