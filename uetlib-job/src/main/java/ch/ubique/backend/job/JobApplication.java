package ch.ubique.backend.job;

import ch.ubique.backend.job.commands.JobApplicationArgsParser;
import ch.ubique.backend.job.commands.JobApplicationJobToStartArgs;
import ch.ubique.backend.job.model.JobRunArguments;
import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;
import ch.ubique.backend.job.service.JobExecutorService;
import ch.ubique.backend.job.service.JobService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

public class JobApplication {

    private static final Logger logger = LoggerFactory.getLogger(JobApplication.class);

    private JobApplication() {}

    public static void run(final Class<?> applicationClass, final String[] args) {
        logger.info("{} started with args {}", applicationClass, args);

        final var jobApplicationArgs = new JobApplicationArgsParser().parse(args);
        if (jobApplicationArgs.isEmpty()) {
            return; // Args could not be parsed -> do not continue
        }

        final var jobToStartArgs = jobApplicationArgs.get().jobToStart();

        if (jobToStartArgs != null) {
            logger.info("Running job now. args = {}", jobToStartArgs);
            runJobNow(applicationClass, jobToStartArgs);
        } else {
            logger.info("Starting Spring application now.");
            SpringApplication.run(applicationClass, args);
        }
    }

    private static void runJobNow(
            final Class<?> applicationClass, final JobApplicationJobToStartArgs jobToStartArgs) {

        final var context =
                new SpringApplicationBuilder(applicationClass).web(WebApplicationType.NONE).run();

        final var jobClassName = jobToStartArgs.jobClassName();

        try {
            final var jobClass = context.getBean(JobService.class).getJobClass(jobClassName);
            context.getBean(JobExecutorService.class)
                    .submit(
                            jobClass,
                            new JobRunArguments(jobToStartArgs.forceRun()),
                            jobToStartArgs.jobRunId(),
                            JobRunnerKey.CURRENT);

            logger.info("Completed job, going to shut down");
        } catch (final ClassNotFoundException e) {
            logger.error("The specified job {} can not be found", jobClassName, e);
        }

        final var exitCode = SpringApplication.exit(context);
        logger.info("Shutdown completed. Exit code {}", exitCode);
        System.exit(exitCode);
    }
}
