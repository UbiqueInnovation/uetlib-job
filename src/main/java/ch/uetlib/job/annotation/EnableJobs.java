package ch.uetlib.job.annotation;

import ch.uetlib.job.Job;
import ch.uetlib.job.RegisteredJob;

import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * Place this annotation on any class in a package that matches (or is prefixed by) your maven
 * artifact group id. This annotation is only discovered in a package below the group id.
 *
 * <p>The package 'ch.uetlib.job' is NOT always included, to use the default {@link
 * ch.uetlib.job.health.HealthCheckJob} and {@link
 * ch.uetlib.job.cleanup.CleanupJob}, include this package or the desired subpackages.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import(RegisteredJobRegistrar.class)
public @interface EnableJobs {
    /** Alias for {@link #basePackages} */
    @AliasFor("basePackages")
    String[] value() default {};

    /**
     * Alias for {@link #value}
     *
     * <p>Base packages to scan for annotated components.
     *
     * <p>Use {@link #basePackageClasses()} for a type-safe alternative to String-based package
     * names.
     *
     * @return the array of 'basePackages'.
     */
    @AliasFor("value")
    String[] basePackages() default {};

    /**
     * Type-safe alternative to {@link #basePackages()} for specifying the packages to scan for
     * annotated components. The package of each class specified will be scanned.
     *
     * <p>Consider creating a special no-op marker class or interface in each package that serves no
     * purpose other than being referenced by this attribute.
     *
     * @return the array of 'basePackageClasses'.
     */
    Class<?>[] basePackageClasses() default {};

    /**
     * List of classes annotated with @{@link RegisteredJob}. If not empty, disables classpath
     * scanning.
     *
     * @return array of {@link Job} classes
     */
    Class<?>[] jobs() default {};
}
