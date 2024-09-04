package ch.ubique.backend.job.service.triggers;

import ch.ubique.backend.job.model.JobRunArguments;
import ch.ubique.backend.job.model.triggers.FileTrigger;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Service
@Profile("!job-run")
public class FileTriggerService implements TriggerService<FileTrigger>, AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(FileTriggerService.class);

    private final ExecutorService executorService;
    private final FileTriggerWatcher fileTriggerWatcher;

    public FileTriggerService(final FileTriggerWatcher fileTriggerWatcher) {
        final var threadFactory =
                new ThreadFactoryBuilder().setNameFormat("file-trigger-service").build();
        this.executorService = Executors.newSingleThreadExecutor(threadFactory);
        this.fileTriggerWatcher = fileTriggerWatcher;
        executorService.execute(fileTriggerWatcher::watch);
    }

    @Override
    public void addTrigger(
            final int triggerId,
            final FileTrigger triggerDefinition,
            final Consumer<JobRunArguments> consumer) {
        fileTriggerWatcher.registerFileTrigger(triggerId, triggerDefinition, consumer);
    }

    @Override
    public void removeTrigger(final int triggerId) {
        fileTriggerWatcher.removeFileTrigger(triggerId);
    }

    @Override
    public void close() {
        executorService.shutdown();
        try {
            final var successfulTermination = executorService.awaitTermination(5, TimeUnit.SECONDS);
            if (!successfulTermination) {
                logger.info(
                        "executorService failed to terminate successfully before timeout elapsed");
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
