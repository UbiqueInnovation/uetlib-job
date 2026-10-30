package ch.ubique.uetlib.job;

import ch.ubique.uetlib.job.commands.JobApplicationArgsParser;
import ch.ubique.uetlib.job.commands.JobApplicationJobToStartArgs;
import ch.ubique.uetlib.job.model.JobRunArguments;
import ch.ubique.uetlib.job.model.JobRunArgumentsAdditionalData;
import ch.ubique.uetlib.job.model.JobRunArgumentsAdditionalDataString;
import ch.ubique.uetlib.job.model.executer.SuccessfulSubmissionResult;
import ch.ubique.uetlib.job.model.properties.enums.JobRunnerKey;
import ch.ubique.uetlib.job.model.runner.JobApplicationExitStatus;
import ch.ubique.uetlib.job.service.JobService;
import ch.ubique.uetlib.job.service.core.ClassLoaderService;
import ch.ubique.uetlib.job.service.executor.JobExecutorService;
import ch.ubique.uetlib.job.service.executor.runners.DedicatedJobRunner;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@SuppressWarnings("unused")
public final class JobApplication {

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

        final var applicationContext =
                new SpringApplicationBuilder(applicationClass)
                        .profiles("job-run", "no-security")
                        .web(WebApplicationType.NONE)
                        .run(args);

        final var exitCode = tryToRunJob(jobToStartArgs, applicationContext).exitCode();
        logger.info("Completed job, going to shut down");
        SpringApplication.exit(applicationContext, () -> exitCode);
        logger.info("Shutdown completed. Exit code {}", exitCode);
        System.exit(exitCode);
    }

    private static JobApplicationExitStatus tryToRunJob(
            final JobApplicationJobToStartArgs jobToStartArgs,
            final ConfigurableApplicationContext applicationContext) {
        try {
            final var additionalData = getAdditionalData(jobToStartArgs, applicationContext);
            final var submissionResult =
                    applicationContext
                            .getBean(JobExecutorService.class)
                            .submit(
                                    applicationContext
                                            .getBean(ClassLoaderService.class)
                                            .getJobClass(jobToStartArgs.jobClassName()),
                                    new JobRunArguments(
                                            jobToStartArgs.forceRun(),
                                            null,
                                            null,
                                            null,
                                            additionalData),
                                    jobToStartArgs.jobRunId(),
                                    JobRunnerKey.DEDICATED_K8S_JOB);

            if (!(submissionResult
                    instanceof final SuccessfulSubmissionResult successfulSubmissionResult)) {
                logger.info("Job run {} submission failed.", jobToStartArgs.jobRunId());
                return JobApplicationExitStatus.COULD_NOT_SCHEDULE;
            }

            logger.info("Waiting for job to finish.");
            applicationContext.getBean(DedicatedJobRunner.class).awaitCompletion();

            final var jobRunId = successfulSubmissionResult.jobRunId();
            return exitCodeFromJobRunState(applicationContext, jobRunId);
        } catch (final ClassNotFoundException e) {
            logger.error("The specified job or additional data class can not be found", e);
            return JobApplicationExitStatus.JOB_NOT_FOUND;
        } catch (final JacksonException e) {
            logger.error("Could not deserialize additional data.", e);
            return JobApplicationExitStatus.BAD_ARGUMENTS;
        } catch (final Exception e) {
            logger.error("Unexpected exception.", e);
            return JobApplicationExitStatus.UNCAUGHT_EXCEPTION;
        }
    }

    private static JobApplicationExitStatus exitCodeFromJobRunState(
            final ConfigurableApplicationContext applicationContext, final int jobRunId) {
        final var jobRunState =
                applicationContext.getBean(JobService.class).getJobRun(jobRunId).latestState();
        final var exitStatus =
                switch (jobRunState) {
                    case DONE, SKIPPED, ACKNOWLEDGED -> JobApplicationExitStatus.SUCCESS;
                    case WARNING, UNFINISHED, CANCELLED, SCHEDULING_FAILED ->
                            JobApplicationExitStatus.JOB_WARNING;
                    case ERROR -> JobApplicationExitStatus.JOB_ERROR;
                    case BUSY, SCHEDULED -> JobApplicationExitStatus.JOB_NOT_TERMINATED;
                };
        logger.info(
                "Job run {} finished with state {}. Exit status = {}, exit code = {}.",
                jobRunId,
                jobRunState,
                exitStatus,
                exitStatus.exitCode());
        return exitStatus;
    }

    @Nullable
    private static JobRunArgumentsAdditionalData getAdditionalData(
            final JobApplicationJobToStartArgs args, final ApplicationContext applicationContext)
            throws ClassNotFoundException, JacksonException {
        final var additionalDataString = args.additionalDataString();
        if (additionalDataString == null) {
            return null;
        }

        final var additionalDataClassName = args.additionalDataClassName();
        if (StringUtils.isBlank(additionalDataClassName)) {
            return new JobRunArgumentsAdditionalDataString(additionalDataString);
        }

        final var additionalDataClass =
                applicationContext
                        .getBean(ClassLoaderService.class)
                        .getClassForName(additionalDataClassName);

        if (!JobRunArgumentsAdditionalData.class.isAssignableFrom(additionalDataClass)) {
            throw new ClassCastException(
                    "Additional data class ("
                            + additionalDataClassName
                            + ") must implement "
                            + JobRunArgumentsAdditionalData.class.getCanonicalName());
        }

        // Cast must hold, by the check above
        return (JobRunArgumentsAdditionalData)
                applicationContext
                        .getBean(JsonMapper.class)
                        .readValue(additionalDataString, additionalDataClass);
    }
}
