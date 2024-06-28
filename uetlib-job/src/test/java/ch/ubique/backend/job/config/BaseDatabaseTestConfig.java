package ch.ubique.backend.job.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;

import java.text.MessageFormat;
import java.util.List;
import java.util.stream.Stream;

public abstract class BaseDatabaseTestConfig {

    @Bean
    abstract String databaseSpecificProfileName();

    @Bean
    List<ClassPathResource> databaseSpecificInitializationScripts(
            @Qualifier("databaseSpecificProfileName") final String databaseSpecificProfileName) {
        return Stream.of("insert_jobs.sql")
                .map(
                        databaseInitializationScript ->
                                new ClassPathResource(
                                        MessageFormat.format(
                                                "sql/{0}/{1}",
                                                databaseSpecificProfileName,
                                                databaseInitializationScript)))
                .toList();
    }
}
