package ch.ubique.backend.job.annotation;

import ch.ubique.backend.job.RegisteredJob;
import ch.ubique.backend.job.definitions.RegisteredJobDefinitions;
import ch.ubique.backend.storage.annotations.utils.EnableAnnotationUtils;

import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;

import java.text.MessageFormat;
import java.util.Set;

public class RegisteredJobRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware {

    private static final Logger logger = LoggerFactory.getLogger(RegisteredJobRegistrar.class);

    private Environment environment;

    private static @NotNull Class<?> loadEnableJobsClass(
            final @NotNull AnnotationMetadata importingClassMetadata) {
        final Class<?> enableJobsClass;
        try {
            enableJobsClass = Class.forName(importingClassMetadata.getClassName());
        } catch (final ClassNotFoundException e) {
            throw new IllegalStateException(
                    MessageFormat.format(
                            "Unable to load class {0}, the class that is annotated with @{1}",
                            importingClassMetadata.getClassName(), EnableJobs.class),
                    e);
        }
        return enableJobsClass;
    }

    @Override
    public void registerBeanDefinitions(
            final @NotNull AnnotationMetadata importingClassMetadata,
            final @NotNull BeanDefinitionRegistry registry) {
        //        if (!environment.matchesProfiles("!job-run")) {
        //            return;
        //        }

        registerRegisteredJobClassesBeanDefinition(
                (DefaultListableBeanFactory) registry, importingClassMetadata);
    }

    private void registerRegisteredJobClassesBeanDefinition(
            final @NotNull DefaultListableBeanFactory beanFactory,
            final @NotNull AnnotationMetadata importingClassMetadata) {
        final var mergedAnnotationsSearch =
                MergedAnnotations.search(MergedAnnotations.SearchStrategy.TYPE_HIERARCHY)
                        .withEnclosingClasses(
                                clazz ->
                                        !ClassUtils.isInnerClass(clazz)
                                                && !ClassUtils.isStaticClass(clazz));

        final var enableJobsClass = loadEnableJobsClass(importingClassMetadata);

        final var enableJobsAnnotation =
                importingClassMetadata.getAnnotations().get(EnableJobs.class).synthesize();

        final var registeredJobClasses =
                findAllRegisteredJobClasses(
                        enableJobsClass, enableJobsAnnotation, mergedAnnotationsSearch);

        logger.info("found registeredJobClasses {}", registeredJobClasses);

        final var beanDefinitionBuilder =
                BeanDefinitionBuilder.rootBeanDefinition(RegisteredJobDefinitions.class).addConstructorArgValue(registeredJobClasses);
        beanFactory.registerBeanDefinition(
                "registeredJobDefinitions", beanDefinitionBuilder.getBeanDefinition());
    }

    private Set<Class<?>> findAllRegisteredJobClasses(
            final Class<?> enableJobsClass,
            final EnableJobs enableJobsAnnotation,
            final MergedAnnotations.Search mergedAnnotationsSearch) {
        final var storageClasses =
                EnableAnnotationUtils.findAllClassesBy(
                        enableJobsAnnotation.jobs(),
                        enableJobsAnnotation.basePackages(),
                        enableJobsAnnotation.basePackageClasses(),
                        enableJobsClass.getPackageName(),
                        RegisteredJob.class);

        return EnableAnnotationUtils.filterClassesByProfileEnabled(
                environment, mergedAnnotationsSearch, storageClasses);
    }

    @Override
    public void setEnvironment(final @NotNull Environment environment) {
        this.environment = environment;
    }
}
