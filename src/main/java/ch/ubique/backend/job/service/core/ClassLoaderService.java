package ch.ubique.backend.job.service.core;

import ch.ubique.backend.job.Job;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public final class ClassLoaderService {

    private final ApplicationContext applicationContext;

    public ClassLoaderService(final ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public Class<? extends Job> getJobClass(final String className) throws ClassNotFoundException {
        return getClassForName(className, Job.class);
    }

    public @Nullable Class<? extends Job> getJobClassIfExists(final String className) {
        try {
            return getClassForName(className, Job.class);
        } catch (final ClassNotFoundException e) {
            return null;
        }
    }

    public Class<?> getClassForName(final String className) throws ClassNotFoundException {
        final var classLoader = Objects.requireNonNull(applicationContext.getClassLoader());
        return classLoader.loadClass(className);
    }

    public <T> Class<? extends T> getClassForName(
            final String className, final Class<T> targetClass) throws ClassNotFoundException {
        final var cls = getClassForName(className);
        try {
            return cls.asSubclass(targetClass);
        } catch (final ClassCastException e) {
            throw new ClassCastException("Class " + cls + " can not be assigned to " + targetClass);
        }
    }
}
