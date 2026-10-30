package ch.ubique.uetlib.job.test.config;

import ch.ubique.uetlib.db.jdbc.VendorLocationResolver;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;

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
