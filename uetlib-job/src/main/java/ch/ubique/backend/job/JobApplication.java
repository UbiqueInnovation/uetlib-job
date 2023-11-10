package ch.ubique.backend.job;

import ch.ubique.backend.job.commands.JobApplicationArgsParser;
import ch.ubique.backend.job.commands.JobApplicationJobToStartArgs;
import ch.ubique.backend.job.model.JobRunArguments;
import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;
import ch.ubique.backend.job.service.JobService;
import ch.ubique.backend.job.service.core.ClassLoaderService;
import ch.ubique.backend.job.service.executor.JobExecutorService;
import ch.ubique.backend.job.service.executor.runners.CurrentJobRunner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationContext;

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
            runJobNow(applicationClass, jobToStartArgs, args);
        } else {
            logger.info("Starting Spring application now.");
            SpringApplication.run(applicationClass, args);
        }
    }

    private static void runJobNow(
            final Class<?> applicationClass,
            final JobApplicationJobToStartArgs jobToStartArgs,
            final String[] args) {

        final var context =
                new SpringApplicationBuilder(applicationClass)
                        .profiles("job-run", "no-security")
                        .web(WebApplicationType.NONE)
                        .run(args);

        final var jobClassName = jobToStartArgs.jobClassName();

        try {
            final var jobClass = context.getBean(JobService.class).getJobClass(jobClassName);
            context.getBean(JobExecutorService.class)
                    .submit(
                            jobClass,
                            new JobRunArguments(
                                    jobToStartArgs.forceRun(),
                                    getAdditionalData(jobToStartArgs, context)),
                            jobToStartArgs.jobRunId(),
                            JobRunnerKey.CURRENT);

            logger.info("Waiting for job to finish.");
            context.getBean(CurrentJobRunner.class).shutdownAndWaitForJobsToComplete();

            logger.info("Completed job, going to shut down");
        } catch (final ClassNotFoundException e) {
            logger.error("The specified job or additional data class can not be found", e);
        } catch (final JsonProcessingException e) {
            logger.error("Could not deserialize additional data.", e);
        }

        final var exitCode = SpringApplication.exit(context);
        logger.info("Shutdown completed. Exit code {}", exitCode);
        System.exit(exitCode);
    }

    private static Object getAdditionalData(
            final JobApplicationJobToStartArgs args, final ApplicationContext context)
            throws ClassNotFoundException, JsonProcessingException {
        final var additionalDataString = args.additionalDataString();
        if (additionalDataString == null) {
            return null;
        }

        final var additionalDataClassName = args.additionalDataClassName();
        if (StringUtils.isBlank(additionalDataClassName)) {
            return additionalDataString;
        }

        final var additionalDataClass =
                context.getBean(ClassLoaderService.class).getClassForName(additionalDataClassName);

        return context.getBean(ObjectMapper.class)
                .readValue(additionalDataString, additionalDataClass);
    }
}
