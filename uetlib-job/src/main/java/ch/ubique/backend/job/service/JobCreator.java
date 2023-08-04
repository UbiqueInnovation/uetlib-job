package ch.ubique.backend.job.service;

import ch.ubique.backend.job.BaseJob;
import ch.ubique.backend.job.model.Job;
import ch.ubique.backend.job.scheduler.JobAutowireConstructor;

import jakarta.validation.constraints.NotNull;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.BeanFactoryAnnotationUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
public final class JobCreator {

    private final ApplicationContext applicationContext;

    public JobCreator(final ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    private Class<BaseJob> getClassForName(final String clazz) throws ClassNotFoundException {
        final var classLoader = Objects.requireNonNull(applicationContext.getClassLoader());
        //noinspection unchecked
        return (Class<BaseJob>) classLoader.loadClass(clazz);
    }

    public BaseJob createNewJobInstance(final String className) throws ClassNotFoundException {
        final var jobClass = getClassForName(className);
        return createNewJobInstance(jobClass);
    }

    public BaseJob createNewJobInstance(final Job job) throws ClassNotFoundException {
        return createNewJobInstance(job.clazzName());
    }

    public <T extends BaseJob> T createNewJobInstance(final Class<T> jobClass) {
        final var parameters = getConstructor(jobClass).getParameters();
        final var jobConstructorParameters =
                Arrays.stream(parameters).map(this::getBeanForParameter).toArray();
        final var parameterTypes =
                Arrays.stream(parameters).map(Parameter::getType).toArray(Class<?>[]::new);
        try {
            return jobClass.getConstructor(parameterTypes).newInstance(jobConstructorParameters);
        } catch (final InstantiationException
                | IllegalAccessException
                | InvocationTargetException
                | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private Constructor<?> getConstructor(final Class<? extends BaseJob> jobClass) {
        final var constructors = jobClass.getConstructors();
        if (constructors.length == 1) {
            // @JobAutowireConstructor annotation is not needed if there is only one constructor
            return constructors[0];
        }
        return Arrays.stream(jobClass.getConstructors())
                .filter(c -> c.isAnnotationPresent(JobAutowireConstructor.class))
                .findFirst()
                .orElseThrow(
                        () ->
                                new NoSuchElementException(
                                        MessageFormat.format(
                                                "Either declare a single, default, public"
                                                        + " constructor or annotate one with annotation"
                                                        + " ''@{0}'' for job ''{1}''",
                                                JobAutowireConstructor.class.getCanonicalName(),
                                                jobClass.getCanonicalName())));
    }

    @NotNull
    private Object getBeanForParameter(final Parameter p) {
        final var qualifierAnnotations = p.getDeclaredAnnotationsByType(Qualifier.class);
        if (qualifierAnnotations.length > 0) {
            // in case there is a qualifier specified, get bean by qualifier (no fallback)
            final var qualifier = qualifierAnnotations[0].value();
            return BeanFactoryAnnotationUtils.qualifiedBeanOfType(
                    applicationContext.getAutowireCapableBeanFactory(), p.getType(), qualifier);
        }

        if (p.getType().isAssignableFrom(applicationContext.getClass())) {
            return applicationContext;
        }

        try {
            // in case there are multiple beans for the same class try
            // getting bean by class and name first
            return applicationContext.getBean(p.getName(), p.getType());
        } catch (final BeansException e1) {
            try {
                // don't enforce name matching if there is only one bean of
                // the requested class
                return applicationContext.getBean(p.getType());
            } catch (final BeansException e2) {
                // Features such as AOP may "enhance" the registered bean instances,
                // such that the type is now different to the original type.
                // Thus getting the bean by type will fail.
                // E.g. com.example.MyBean may become com.example.MyBean$$EnhancerBySpringCGLIB
                //      but the name, if not explicitly modified, is myBean in both cases
                return applicationContext.getBean(p.getName());
            }
        }
    }
}
