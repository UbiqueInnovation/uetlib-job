package ch.ubique.backend.job.annotation;

import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.RegisteredJob;

import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * Place this annotation on any class in a package that matches (or is prefixed by) your maven
 * artifact group id. This annotation is only discovered in a package below the group id.
 *
 * <p>The package 'ch.ubique.backend' is always included, so this framework's own jobs (e.g. {@code
 * CleanupJob}) are always registered alongside whatever {@link #basePackages()} (or {@link
 * #basePackageClasses()}) resolves to. This does not apply when {@link #jobs()} is set, since that
 * disables classpath scanning entirely.
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
