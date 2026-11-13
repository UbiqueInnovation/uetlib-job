package ch.uetlib.job.autoconfigure;

import static org.junit.jupiter.api.Assertions.*;

import ch.uetlib.job.model.spring.JobQueueDataSourceProvider;
import ch.uetlib.job.model.spring.PgListenJobTriggerDataSourceProvider;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

import java.util.List;

/**
 * The default providers must give way to the application's own, even when the application's
 * component scan reaches {@code ch.uetlib} before the configuration that defines them. As plain
 * configurations they once didn't, and the application failed to start because the bean name was
 * taken.
 */
class UetlibJobDataSourceProviderAutoConfigurationTest {

    private static final DataSourceProperties OWN_PROPERTIES = new DataSourceProperties();

    @Test
    void ownProvidersWinOverTheDefaultsWhateverTheScanOrder() {
        try (final var context = context(ScanningUetlibBeforeOwnProvidersConfig.class)) {
            assertSame(
                    OWN_PROPERTIES,
                    context.getBean(JobQueueDataSourceProvider.class).getDataSourceProperties());
            assertSame(
                    OWN_PROPERTIES,
                    context.getBean(PgListenJobTriggerDataSourceProvider.class)
                            .getDataSourceProperties());
        }
    }

    @Test
    void defaultProvidersUseTheAmbientConnectionDetails() {
        try (final var context = context(AmbientConnectionDetailsConfig.class)) {
            assertEquals(
                    "jdbc:postgresql://ambient/db",
                    context.getBean(JobQueueDataSourceProvider.class)
                            .getDataSourceProperties()
                            .getUrl());
            assertEquals(
                    "jdbc:postgresql://ambient/db",
                    context.getBean(PgListenJobTriggerDataSourceProvider.class)
                            .getDataSourceProperties()
                            .getUrl());
        }
    }

    /**
     * Without this registration nothing loads it: a component scan with Spring Boot's {@link
     * AutoConfigurationExcludeFilter} skips it, and single-datasource applications would get no
     * provider at all. The tests above import it directly and would not notice.
     */
    @Test
    void defaultProvidersAreRegisteredAsAnAutoConfiguration() {
        final List<String> autoConfigurations =
                ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader())
                        .getCandidates();
        assertTrue(
                autoConfigurations.contains(
                        UetlibJobDataSourceProviderAutoConfiguration.class.getName()));
    }

    private static AnnotationConfigApplicationContext context(final Class<?> configClass) {
        final var context = new AnnotationConfigApplicationContext();
        // Like Spring Boot, which disables it by default.
        context.setAllowBeanDefinitionOverriding(false);
        context.register(configClass);
        context.refresh();
        return context;
    }

    /**
     * Its scan is processed before its import, so the scanned default configuration comes before
     * the application's providers, the order in which the bug showed.
     */
    @Configuration(proxyBeanMethods = false)
    @ComponentScan(
            basePackageClasses = UetlibJobDataSourceProviderAutoConfiguration.class,
            useDefaultFilters = false,
            includeFilters =
                    @Filter(
                            type = FilterType.ASSIGNABLE_TYPE,
                            classes = UetlibJobDataSourceProviderAutoConfiguration.class),
            excludeFilters =
                    @Filter(
                            type = FilterType.CUSTOM,
                            classes = AutoConfigurationExcludeFilter.class))
    @Import(OwnProvidersConfig.class)
    @ImportAutoConfiguration(UetlibJobDataSourceProviderAutoConfiguration.class)
    static class ScanningUetlibBeforeOwnProvidersConfig {}

    @Configuration(proxyBeanMethods = false)
    static class OwnProvidersConfig {

        @Bean
        JobQueueDataSourceProvider jobQueueDataSourceProvider() {
            return () -> OWN_PROPERTIES;
        }

        @Bean
        PgListenJobTriggerDataSourceProvider pgListenJobTriggerDataSourceProvider() {
            return () -> OWN_PROPERTIES;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration(UetlibJobDataSourceProviderAutoConfiguration.class)
    static class AmbientConnectionDetailsConfig {

        @Bean
        JdbcConnectionDetails jdbcConnectionDetails() {
            return new JdbcConnectionDetails() {
                @Override
                public String getUsername() {
                    return "user";
                }

                @Override
                public String getPassword() {
                    return "password";
                }

                @Override
                public String getJdbcUrl() {
                    return "jdbc:postgresql://ambient/db";
                }
            };
        }
    }
}
