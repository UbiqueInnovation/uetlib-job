package ch.uetlib.job.config;

import org.hibernate.cfg.QuerySettings;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class HibernateJsonConfiguration {
    @Bean
    HibernatePropertiesCustomizer enableHibernateJsonFunctions() {
        return hibernateProperties ->
                hibernateProperties.put(QuerySettings.JSON_FUNCTIONS_ENABLED, true);
    }
}
