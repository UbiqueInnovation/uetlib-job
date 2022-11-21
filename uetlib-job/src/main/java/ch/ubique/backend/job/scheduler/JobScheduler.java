package ch.ubique.backend.job.scheduler;

import ch.ubique.backend.job.BaseJob;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.Job;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.BeanFactoryAnnotationUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;

import javax.annotation.PostConstruct;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.NoSuchElementException;

public abstract class JobScheduler {

    private final SchedulerService schedulerService;
    private final JobDataService jobDataService;
    private final ApplicationContext applicationContext;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    protected JobScheduler(
            final SchedulerService schedulerService,
            final JobDataService jobDataService,
            final ApplicationContext applicationContext) {
        this.schedulerService = schedulerService;
        this.jobDataService = jobDataService;
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    public void init()
            throws ClassNotFoundException, InvocationTargetException, NoSuchMethodException,
                    InstantiationException, IllegalAccessException {
        for (final var job : jobDataService.findJobs()) {
            try {
                final var instance = createNewJobInstance(job);
                if (job.isCronActive() && job.isCronExpressionValid()) {
                    schedulerService.scheduleJob(job.getClazz(), instance, job.getCronExpression());
                }
            } catch (NoClassDefFoundError e) {
                logger.warn("Could not find class for {}. Removing database entry.", job.getClazz());
                jobDataService.delete(job);
            }

        }
    }

    protected abstract Class<BaseJob> getClassForName(String clazz) throws ClassNotFoundException;

    public void update(final Job job)
            throws ClassNotFoundException, InvocationTargetException, NoSuchMethodException,
                    InstantiationException, IllegalAccessException {
        if (!job.isCronActive() || !job.isCronExpressionValid()) {
            schedulerService.removeScheduleJob(job.getClazz());
            return;
        }

        final var jobBean = createNewJobInstance(job);
        schedulerService.updateJob(job.getClazz(), jobBean, job.getCronExpression());
    }

    public void runNow(final Job job, boolean forceRun)
            throws ClassNotFoundException, InvocationTargetException, NoSuchMethodException,
                    InstantiationException, IllegalAccessException {
        schedulerService.runJobNowManual(job.getClazz(), createNewJobInstance(job), forceRun);
    }

    public BaseJob createNewJobInstance(Job job)
            throws ClassNotFoundException, InvocationTargetException, NoSuchMethodException,
                    InstantiationException, IllegalAccessException {
        final var jobClass = getClassForName(job.getClazz());
        return createNewJobInstance(jobClass);
    }

    public BaseJob createNewJobInstance(final Class<? extends BaseJob> jobClass)
            throws NoSuchMethodException, InvocationTargetException, InstantiationException,
                    IllegalAccessException {
        final var parameters = getConstructor(jobClass).getParameters();
        final var jobConstructorParameters =
                Arrays.stream(parameters).map(this::getBeanForParameter).toArray();
        final var parameterTypes =
                Arrays.stream(parameters).map(Parameter::getType).toArray(Class<?>[]::new);
        return jobClass.getConstructor(parameterTypes).newInstance(jobConstructorParameters);
    }

    private Constructor<?> getConstructor(Class<? extends BaseJob> jobClass) {
        final var constructors = jobClass.getConstructors();
        if (constructors.length == 1) {
            // @JobAutowireConstructor annotation is not needed if there is only one constructor
            return constructors[0];
        }
        return Arrays.stream(jobClass.getConstructors())
                .filter(c -> c.isAnnotationPresent(JobAutowireConstructor.class))
                .findFirst()
                .orElseThrow(
                        () -> {
                            throw new NoSuchElementException(
                                    MessageFormat.format(
                                            "Either declare a single, default, public constructor"
                                                + " or annotate one with annotation ''@{0}''",
                                            JobAutowireConstructor.class.getCanonicalName(),
                                            jobClass.getCanonicalName()));
                        });
    }

    @NotNull
    private Object getBeanForParameter(Parameter p) {
        final var qualifierAnnotations = p.getDeclaredAnnotationsByType(Qualifier.class);
        if (qualifierAnnotations.length > 0) {
            // in case there is a qualifier specified, get bean by qualifier (no fallback)
            final var qualifier = qualifierAnnotations[0].value();
            return BeanFactoryAnnotationUtils.qualifiedBeanOfType(
                    applicationContext.getAutowireCapableBeanFactory(), p.getType(), qualifier);
        }

        try {
            // in case there are multiple beans for the same class try
            // getting bean by class and name first
            return applicationContext.getBean(p.getName(), p.getType());
        } catch (BeansException e1) {
            try {
                // don't enforce name matching if there is only one bean of
                // the requested class
                return applicationContext.getBean(p.getType());
            } catch (BeansException e2) {
                // Features such as AOP may "enhance" the registered bean instances,
                // such that the type is now different to the original type.
                // Thus getting the bean by type will fail.
                // E.g. com.example.MyBean may become com.example.MyBean$$EnhancerBySpringCGLIB
                //      but the name if not explicitly modified is myBean in both cases
                return applicationContext.getBean(p.getName());
            }
        }
    }
}
