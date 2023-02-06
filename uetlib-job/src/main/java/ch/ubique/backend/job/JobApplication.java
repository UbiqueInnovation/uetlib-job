package ch.ubique.backend.job;

import ch.ubique.backend.job.scheduler.JobScheduler;
import ch.ubique.backend.job.service.JobCreator;
import java.lang.reflect.InvocationTargetException;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

public class JobApplication {

    private JobApplication() {}

    private static final Logger logger = LoggerFactory.getLogger(JobApplication.class);

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

        logger.info("{} started with args {}", applicationClass, args);

        // Args parsing
        final var options = new Options();
        final var help = new Option("h", "help", false, "print this message");
        final var jobClassOption =
                new Option("j", "job", true, "Fully qualified name of the job class to run now.");
        final var jobRunIdOption =
                new Option(
                        "r", "run", true, "Job run id. Needed, if there already exists a job run.");
        final var forceRunOption =
                new Option("f", "force", false, "Runs the job with the force option enabled.");
        options.addOption(help);
        options.addOption(jobClassOption);
        options.addOption(jobRunIdOption);
        options.addOption(forceRunOption);

        final var parser = new DefaultParser();
        final var helpFormatter = new HelpFormatter();

        final CommandLine command;
        try {
            command = parser.parse(options, args, false);
        } catch (final ParseException e) {
            logger.warn("Unexpected args {}", args, e);
            helpFormatter.printHelp("java", options);
            return;
        }
        if (command.hasOption(help) || command.getArgs().length > 0) {
            helpFormatter.printHelp("java", options);
            return;
        }

        final var context = SpringApplication.run(applicationClass, args);

        if (command.hasOption(jobClassOption)) {
            final var jobClassName = command.getOptionValue(jobClassOption);
            final var job = context.getBean(JobCreator.class).createNewJobInstance(jobClassName);
            final var forceRun = command.hasOption(forceRunOption);
            job.jobDataMap.put("forceRun", forceRun);
            logger.info("Starting {}, forceRun = {}", jobClassName, forceRun);
            if (command.hasOption(jobRunIdOption)) {
                final var runId = Integer.parseInt(command.getOptionValue(jobRunIdOption));
                job.jobRun().setId(runId);
                logger.info("Setting run id {}", runId);
            }
            job.run(false);
            logger.info("Completed job, going to shut down");

            final var exitCode = SpringApplication.exit(context);
            logger.info("Shutdown completed. Exit code {}", exitCode);
            System.exit(exitCode);
        } else {
            registerJobs(applicationClass, context);
            context.getBean(JobScheduler.class).scheduleAllRegisteredJobs();
        }
    }

    private static void registerJobs(
            final Class<?> applicationClass, final ConfigurableApplicationContext context)
            throws InvocationTargetException, NoSuchMethodException, InstantiationException,
                    IllegalAccessException {

        final var reflections = new Reflections(applicationClass.getPackage().getName());
        for (final var cls : reflections.getTypesAnnotatedWith(RegisteredJob.class)) {
            //noinspection unchecked
            final var jobClass = (Class<? extends BaseJob>) cls;
            final var job = context.getBean(JobCreator.class).createNewJobInstance(jobClass);
            final var properties = job.properties();
            logger.info("Registered {}. Properties = {}", jobClass, properties);
        }
    }
}
