package ch.ubique.backend.job.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Profile("test")
@Configuration
@EnableJpaRepositories(basePackages = {"ch.ubique.backend.job"})
@EntityScan(basePackages = {"ch.ubique.backend.job.model.entity"})
public class BaseTestConfig {}
