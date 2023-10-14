package ch.ubique.backend.job.commands;

import org.apache.commons.cli.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class JobApplicationArgsParser {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    final Options options;
    final Option help;
    final Option jobClassOption;
    final Option jobRunIdOption;
    final Option forceRunOption;

    final Option additionalDataClass;
    final Option additionalData;

    public JobApplicationArgsParser() {
        options = new Options();
        help = new Option("h", "help", false, "print this message");
        jobClassOption =
                new Option("j", "job", true, "Fully qualified name of the job class to run now.");
        jobRunIdOption =
                new Option(
                        "r", "run", true, "Job run id. Needed, if there already exists a job run.");
        forceRunOption =
                new Option("f", "force", false, "Runs the job with the force option enabled.");
        additionalData = new Option("a", "additionalData", true, "JSON serialized string value.");
        additionalDataClass =
                new Option(
                        "c",
                        "additionalDataClass",
                        true,
                        "Fully qualified name of the class to be used for deserializing the additional data.");
        options.addOption(help);
        options.addOption(jobClassOption);
        options.addOption(jobRunIdOption);
        options.addOption(forceRunOption);
    }

    public Optional<JobApplicationArgs> parse(final String[] args) {
        final var parser = new DefaultParser();
        final var helpFormatter = new HelpFormatter();

        final CommandLine command;
        try {
            command = parser.parse(options, args, false);
        } catch (final ParseException e) {
            logger.warn("Unexpected args {}", args, e);
            helpFormatter.printHelp("java", options);
            return Optional.empty();
        }
        if (command.hasOption(help) || command.getArgs().length > 0) {
            helpFormatter.printHelp("java", options);
            return Optional.empty();
        }

        return Optional.of(new JobApplicationArgs(getJobToStart(command)));
    }

    private JobApplicationJobToStartArgs getJobToStart(final CommandLine command) {

        if (!command.hasOption(jobClassOption)) {
            return null;
        }

        return new JobApplicationJobToStartArgs(
                command.getOptionValue(jobClassOption),
                Optional.ofNullable(command.getOptionValue(jobRunIdOption))
                        .map(Integer::parseInt)
                        .orElse(null),
                command.hasOption(forceRunOption),
                StringUtils.defaultIfBlank(command.getOptionValue(additionalData), null),
                StringUtils.defaultIfBlank(command.getOptionValue(additionalDataClass), null));
    }
}
