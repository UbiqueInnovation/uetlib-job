package ch.ubique.backend.job.scheduler;

import ch.ubique.backend.job.BaseJob;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.jdbc.JdbcJob;
import ch.ubique.backend.job.model.jdbc.JdbcJobRun;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.NoSuchElementException;
import javax.annotation.PostConstruct;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;

public class JobScheduler {

    private final SchedulerService schedulerService;
    private final JobDataService<JdbcJob, JdbcJobRun> jobDataService;
    private final ApplicationContext applicationContext;

    public JobScheduler(
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
            if (job.isCronActive() && job.isCronExpressionValid()) {
                schedulerService.scheduleJob(
                        job.getClazz(), getJobInstance(job), job.getCronExpression());
            }
        }
    }

    public void update(final JdbcJob job)
            throws ClassNotFoundException, InvocationTargetException, NoSuchMethodException,
                    InstantiationException, IllegalAccessException {
        if (!job.isCronActive() || !job.isCronExpressionValid()) {
            schedulerService.removeScheduleJob(job.getClazz());
            return;
        }

        final var jobBean = getJobInstance(job);
        schedulerService.updateJob(job.getClazz(), jobBean, job.getCronExpression());
    }

    public void runNow(final JdbcJob job)
            throws ClassNotFoundException, InvocationTargetException, NoSuchMethodException,
                    InstantiationException, IllegalAccessException {
        schedulerService.runJobNow(job.getClazz(), getJobInstance(job));
    }

    private BaseJob<JdbcJob, JobRun> getJobInstance(final JdbcJob job)
            throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException,
                    InstantiationException, IllegalAccessException {
        @SuppressWarnings("unchecked")
        final var jobClass = (Class<BaseJob<JdbcJob, JobRun>>) Class.forName(job.getClazz());
        final var parameters =
                Arrays.stream(jobClass.getConstructors())
                        .filter(c -> c.isAnnotationPresent(JobAutowireConstructor.class))
                        .findFirst()
                        .orElseThrow(
                                () -> {
                                    throw new NoSuchElementException(
                                            MessageFormat.format(
                                                    "No constructor with annotation '@{0}' present"
                                                            + " on class {1}",
                                                    JobAutowireConstructor.class,
                                                    jobClass.getCanonicalName()));
                                })
                        .getParameters();
        final var jobConstructorParameters =
                Arrays.stream(parameters)
                        .map(
                                p -> {
                                    try {
                                        // in case there are multiple beans for the same class try
                                        // getting bean by class and name first
                                        return applicationContext.getBean(p.getName(), p.getType());
                                    } catch (BeansException e) {
                                        // don't enforce name matching if there is only one bean of
                                        // the requested class
                                        return applicationContext.getBean(p.getType());
                                    }
                                })
                        .toArray();
        final var parameterTypes =
                Arrays.stream(parameters).map(Parameter::getType).toArray(Class<?>[]::new);
        return jobClass.getConstructor(parameterTypes).newInstance(jobConstructorParameters);
    }
}
