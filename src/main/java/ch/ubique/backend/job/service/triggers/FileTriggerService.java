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

    private final ExecutorService fileTriggerWatcherExecutorService;
    private final FileTriggerWatcher fileTriggerWatcher;

    public FileTriggerService(final FileTriggerWatcher fileTriggerWatcher) {
        final var threadFactory =
                new ThreadFactoryBuilder().setNameFormat("file-trigger-service").build();
        this.fileTriggerWatcherExecutorService = Executors.newSingleThreadExecutor(threadFactory);
        this.fileTriggerWatcher = fileTriggerWatcher;
        fileTriggerWatcherExecutorService.execute(fileTriggerWatcher::watch);
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
        logger.info("Shutting down file trigger watcher");
        fileTriggerWatcherExecutorService.shutdown();
        try {
            final var successfulTermination =
                    fileTriggerWatcherExecutorService.awaitTermination(5, TimeUnit.SECONDS);
            if (!successfulTermination) {
                logger.info(
                        "Failed to shutdown file trigger watcher successfully before timeout elapsed, will force shutting down executorService");
                fileTriggerWatcherExecutorService
                        .shutdownNow(); // Forcibly interrupts threads if needed
            }
        } catch (final InterruptedException e) {
            logger.info(
                    "Interrupted while awaiting termination of file trigger watcher, will force shutting down executorService");
            fileTriggerWatcherExecutorService.shutdownNow(); // Ensure threads are forcibly stopped
            Thread.currentThread().interrupt();
        }
    }
}
