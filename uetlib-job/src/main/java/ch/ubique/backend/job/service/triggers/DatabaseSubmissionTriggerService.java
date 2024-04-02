package ch.ubique.backend.job.service.triggers;

import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.JobRunArguments;
import ch.ubique.backend.job.model.JobRunDatabaseSubmission;
import ch.ubique.backend.job.model.triggers.DatabaseSubmissionTrigger;
import ch.ubique.backend.job.service.JobService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Service
@Profile("!job-run")
public class DatabaseSubmissionTriggerService implements TriggerService<DatabaseSubmissionTrigger> {

    private static final Logger logger =
            LoggerFactory.getLogger(DatabaseSubmissionTriggerService.class);

    private final Map<Integer, DatabaseSubmissionTrigger> registeredTriggers;
    private final Map<Integer, Consumer<JobRunArguments>> triggerCallbacks;
    private final JobDataService jobDataService;
    private final JobService jobService;

    public DatabaseSubmissionTriggerService(
            final JobDataService jobDataService, final JobService jobService) {
        this.jobDataService = jobDataService;
        this.jobService = jobService;
        registeredTriggers = new ConcurrentHashMap<>();
        triggerCallbacks = new ConcurrentHashMap<>();
    }

    @Scheduled(fixedRate = 30, timeUnit = TimeUnit.SECONDS)
    public void checkDatabaseSubmissions() throws ClassNotFoundException {
        for (final var jobRunDatabaseSubmission : jobDataService.findNewDatabaseSubmissions()) {
            onNewDatabaseSubmission(jobRunDatabaseSubmission);
        }
    }

    private void onNewDatabaseSubmission(final JobRunDatabaseSubmission jobRunDatabaseSubmission)
            throws ClassNotFoundException {
        final var jobClass = jobService.getJobClass(jobRunDatabaseSubmission.jobId());
        final var jobRunDatabaseSubmissionArguments =
                jobRunDatabaseSubmission.jobRunDatabaseSubmissionArguments();

        logger.info(
                "Got new database submission for job {} with submission arguments {}.",
                jobClass,
                jobRunDatabaseSubmissionArguments);

        final var jobRunArguments = jobRunDatabaseSubmissionArguments.toJobRunArguments();

        registeredTriggers.entrySet().stream()
                .filter(
                        entry -> {
                            final var trigger = entry.getValue();
                            return trigger.jobClass().equals(jobClass);
                        })
                .forEach(
                        entry -> {
                            final var consumer = triggerCallbacks.get(entry.getKey());
                            logger.info("Calling callback for {}.", entry.getValue());
                            consumer.accept(jobRunArguments);
                        });
        logger.info(
                "Submitted new job run from database submission for job {} with run arguments {}.",
                jobClass,
                jobRunArguments);
        jobDataService.markDatabaseSubmissionAsExecuted(jobRunDatabaseSubmission.id());
    }

    @Override
    public void addTrigger(
            final int triggerId,
            final DatabaseSubmissionTrigger triggerDefinition,
            final Consumer<JobRunArguments> consumer) {
        registeredTriggers.put(triggerId, triggerDefinition);
        triggerCallbacks.put(triggerId, consumer);
    }

    @Override
    public void removeTrigger(final int triggerId) {
        registeredTriggers.remove(triggerId);
        triggerCallbacks.remove(triggerId);
    }
}
