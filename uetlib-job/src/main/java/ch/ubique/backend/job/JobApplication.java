package ch.ubique.backend.job;

import ch.ubique.backend.job.scheduler.JobScheduler;
import ch.ubique.backend.job.service.JobCreator;
import java.lang.reflect.InvocationTargetException;
import org.reflections.Reflections;
import org.springframework.boot.SpringApplication;

public class JobApplication {

    private JobApplication() {}

    public static void run(Class<?> applicationClass, String[] args) {

        try {
            runJobOrStartScheduler(applicationClass, args);
        } catch (final ClassNotFoundException
                | InvocationTargetException
                | NoSuchMethodException
                | InstantiationException
                | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static void runJobOrStartScheduler(Class<?> applicationClass, final String[] args)
            throws ClassNotFoundException, InvocationTargetException, NoSuchMethodException,
                    InstantiationException, IllegalAccessException {

        final var context = SpringApplication.run(applicationClass, args);

        final var jobCreator = context.getBean(JobCreator.class);
        final var jobScheduler = context.getBean(JobScheduler.class);

        final var reflections = new Reflections(applicationClass.getPackage().getName());
        for (final var cls : reflections.getTypesAnnotatedWith(RegisteredJob.class)) {
            //noinspection unchecked
            jobScheduler.register((Class<? extends BaseJob>) cls);
        }

        if (args.length > 0) {
            for (final var jobClassName : args) {
                final var job = jobCreator.createNewJobInstance(jobClassName);
                job.run();
            }
            SpringApplication.exit(context);
        } else {
            jobScheduler.scheduleAllRegisteredJobs();
        }
    }
}
