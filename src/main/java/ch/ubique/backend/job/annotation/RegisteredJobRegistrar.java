package ch.ubique.backend.job.annotation;

import static ch.ubique.backend.storage.spring.annotations.utils.EnableAnnotationUtils.classForName;
import static ch.ubique.backend.storage.spring.annotations.utils.EnableAnnotationUtils.getBasePackages;

import static java.util.Objects.requireNonNull;

import ch.ubique.backend.job.RegisteredJob;
import ch.ubique.backend.storage.spring.annotations.utils.EnableAnnotationUtils;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.annotation.AnnotatedGenericBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;

import java.text.MessageFormat;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

class RegisteredJobRegistrar
        implements ImportBeanDefinitionRegistrar, ResourceLoaderAware, EnvironmentAware {

    private static final Logger logger = LoggerFactory.getLogger(RegisteredJobRegistrar.class);

    private @Nullable ResourceLoader resourceLoader;
    private @Nullable Environment environment;

    private static Class<?> loadEnableJobsClass(final AnnotationMetadata importingClassMetadata) {
        try {
            return Class.forName(importingClassMetadata.getClassName());
        } catch (final ClassNotFoundException e) {
            throw new IllegalStateException(
                    MessageFormat.format(
                            "Unable to load class {0}, the class that is annotated with @{1}",
                            importingClassMetadata.getClassName(), EnableJobs.class),
                    e);
        }
    }

    @Override
    public void registerBeanDefinitions(
            final AnnotationMetadata importingClassMetadata,
            final BeanDefinitionRegistry registry) {
        // This would be a problem if this RegisteredJobDefinitions gets created if the profile
        // job-run is active, but it is unnecessary, so we skip it
        if (environment != null && environment.matchesProfiles("job-run")) {
            return;
        }

        registerRegisteredJobClassesBeanDefinition(
                (DefaultListableBeanFactory) registry, importingClassMetadata);
    }

    private void registerRegisteredJobClassesBeanDefinition(
            final DefaultListableBeanFactory beanFactory,
            final AnnotationMetadata importingClassMetadata) {
        final var enableJobsClass = loadEnableJobsClass(importingClassMetadata);
        final var enableJobsAnnotation =
                importingClassMetadata.getAnnotations().get(EnableJobs.class).synthesize();

        final var registeredJobClasses =
                findAllRegisteredJobClasses(enableJobsClass, enableJobsAnnotation);

        logger.info("found registeredJobClasses {}", registeredJobClasses);

        final var beanDefinitionBuilder =
                BeanDefinitionBuilder.rootBeanDefinition(RegisteredJobDefinitions.class)
                        .addConstructorArgValue(registeredJobClasses);
        beanFactory.registerBeanDefinition(
                "registeredJobDefinitions", beanDefinitionBuilder.getBeanDefinition());
    }

    /**
     * Always scanned in addition to whatever {@link EnableAnnotationUtils#getBasePackages} resolves
     * - see {@link EnableJobs}.
     */
    private static final String FRAMEWORK_BASE_PACKAGE = "ch.ubique.backend";

    private Set<Class<?>> findAllRegisteredJobClasses(
            final Class<?> enableJobsClass, final EnableJobs enableJobsAnnotation) {
        final var candidateComponents = new LinkedHashSet<BeanDefinition>();
        final var jobs = enableJobsAnnotation.jobs();
        if (org.apache.commons.lang3.ArrayUtils.isEmpty(jobs)) {
            final var scanner =
                    EnableAnnotationUtils.getScanner(
                            requireNonNull(environment),
                            requireNonNull(resourceLoader),
                            RegisteredJob.class);
            final var basePackages =
                    new LinkedHashSet<>(
                            getBasePackages(
                                    enableJobsAnnotation.basePackages(),
                                    enableJobsAnnotation.basePackageClasses(),
                                    enableJobsClass.getPackageName()));
            basePackages.add(FRAMEWORK_BASE_PACKAGE);
            for (final var basePackage : basePackages) {
                candidateComponents.addAll(scanner.findCandidateComponents(basePackage));
            }
        } else {
            for (final var jobClass : jobs) {
                candidateComponents.add(new AnnotatedGenericBeanDefinition(jobClass));
            }
        }

        return candidateComponents.stream()
                .filter(AnnotatedBeanDefinition.class::isInstance)
                .map(AnnotatedBeanDefinition.class::cast)
                .map(
                        beanDefinition ->
                                classForName(requireNonNull(beanDefinition.getBeanClassName())))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public void setResourceLoader(final ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void setEnvironment(final Environment environment) {
        this.environment = environment;
    }
}
