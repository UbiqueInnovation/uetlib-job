package ch.ubique.backend.job.scheduler;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface JobAutowireConstructor {
}
