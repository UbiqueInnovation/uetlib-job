package ch.ubique.backend.job.config;

import ch.ubique.backend.db.jdbc.VendorLocationResolver;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import javax.sql.DataSource;

public abstract class BaseDatabaseTestcontainerConfig {

    @Bean
    List<ClassPathResource> databaseSpecificInitializationScripts(
            final DataSource dataSource,
            @Value("${ch.ubique.backend.job.test.sql-locations}") final String[] sqlLocations) {
        final var locations =
                new VendorLocationResolver(dataSource).resolveLocations(List.of(sqlLocations));
        return locations.stream().map(ClassPathResource::new).toList();
    }
}
