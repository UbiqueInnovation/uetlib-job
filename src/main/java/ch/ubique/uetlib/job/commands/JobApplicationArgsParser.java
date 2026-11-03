package ch.ubique.uetlib.job.commands;

import org.apache.commons.cli.*;
import org.apache.commons.cli.help.HelpFormatter;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

public class JobApplicationArgsParser {

    private static final Logger logger = LoggerFactory.getLogger(JobApplicationArgsParser.class);

    final Options options;
    final Option help;
    final Option jobClassOption;
    final Option jobRunIdOption;
    final Option forceRunOption;

    final Option additionalDataClass;
    final Option additionalData;

    public JobApplicationArgsParser() {
        options = new Options();
        help = Option.builder("h").longOpt("help").desc("print this message").get();
        jobClassOption =
                Option.builder("j")
                        .longOpt("job")
                        .hasArg()
                        .desc("Fully qualified name of the job class to run now.")
                        .get();
        jobRunIdOption =
                Option.builder("r")
                        .longOpt("run")
                        .hasArg()
                        .desc("Job run id. Needed, if there already exists a job run.")
                        .get();
        forceRunOption =
                Option.builder("f")
                        .longOpt("force")
                        .desc("Runs the job with the force option enabled.")
                        .get();
        additionalData =
                Option.builder("a")
                        .longOpt("additionalData")
                        .hasArg()
                        .desc("JSON serialized string value.")
                        .get();
        additionalDataClass =
                Option.builder("c")
                        .longOpt("additionalDataClass")
                        .hasArg()
                        .desc(
                                "Fully qualified name of the class to be used for deserializing the"
                                        + " additional data.")
                        .get();
        options.addOption(help);
        options.addOption(jobClassOption);
        options.addOption(jobRunIdOption);
        options.addOption(forceRunOption);
        options.addOption(additionalData);
        options.addOption(additionalDataClass);
    }

    public Optional<JobApplicationArgs> parse(final String[] args) {
        final var parser = new DefaultParser();
        final var helpFormatter = HelpFormatter.builder().get();

        final CommandLine command;
        try {
            command = parser.parse(options, args, false);
        } catch (final ParseException e) {
            logger.warn("Unexpected args {}", args, e);
            printHelp(helpFormatter);
            return Optional.empty();
        }
        if (command.hasOption(help) || command.getArgs().length > 0) {
            printHelp(helpFormatter);
            return Optional.empty();
        }

        return Optional.of(new JobApplicationArgs(getJobToStart(command)));
    }

    private void printHelp(final HelpFormatter helpFormatter) {
        try {
            helpFormatter.printHelp("java", "", options, "", false);
        } catch (final IOException e) {
            logger.error("Could not print help", e);
        }
    }

    @Nullable
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
