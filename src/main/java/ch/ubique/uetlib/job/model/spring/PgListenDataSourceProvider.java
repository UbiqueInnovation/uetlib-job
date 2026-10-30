package ch.ubique.uetlib.job.model.spring;

import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;

public interface PgListenDataSourceProvider {
    DataSourceProperties getDataSourceProperties();
}
