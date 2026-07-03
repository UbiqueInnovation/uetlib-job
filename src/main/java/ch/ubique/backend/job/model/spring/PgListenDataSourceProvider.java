package ch.ubique.backend.job.model.spring;

import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;

public interface PgListenDataSourceProvider {
    DataSourceProperties getDataSourceProperties();
}
