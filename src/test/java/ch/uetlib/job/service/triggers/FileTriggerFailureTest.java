package ch.uetlib.job.service.triggers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.uetlib.job.model.FileTriggerContext;
import ch.uetlib.job.model.JobRunArguments;
import ch.uetlib.job.model.triggers.FileChangeEvent;
import ch.uetlib.job.model.triggers.FileTrigger;
import ch.uetlib.job.service.triggers.files.JavaNioFileWatcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.CannotCreateTransactionException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

class FileTriggerFailureTest {

    @TempDir Path directory;

    @Test
    void databaseExceptionDoesNotStopWatchingLaterFiles() throws Exception {
        final var failedSubmission = new CountDownLatch(1);
        final var successfulSubmissions = new LinkedBlockingQueue<JobRunArguments>();
        final var trigger =
                new FileTrigger(
                        directory.toString(),
                        Pattern.compile(".*\\.txt"),
                        FileChangeEvent.ENTRY_MOVED_TO);

        // Use the real NIO watch loop on every OS, without requiring the Linux native library.
        try (final var service = new FileTriggerService(JavaNioFileWatcher::new)) {
            service.registerFileTrigger(
                    0,
                    trigger,
                    arguments -> {
                        final var context =
                                assertInstanceOf(
                                        FileTriggerContext.class, arguments.triggerContext());
                        if (context.fileName().equals("outage.txt")) {
                            failedSubmission.countDown();
                            // JPA throws this during job submission when PostgreSQL is unavailable.
                            // Previously it escaped the callback and terminated the only watch
                            // task.
                            throw new CannotCreateTransactionException("Database unavailable");
                        }
                        successfulSubmissions.add(arguments);
                    });

            Files.createFile(directory.resolve("outage.txt"));
            assertTrue(
                    failedSubmission.await(10, TimeUnit.SECONDS), "Outage file was not detected");

            // After recovery, a new file must still trigger without restarting the service.
            // The failed event itself is not automatically retried by this fix.
            Files.createFile(directory.resolve("recovered.txt"));
            final var arguments = successfulSubmissions.poll(10, TimeUnit.SECONDS);
            assertNotNull(arguments, "Database failure stopped the file watcher");
            final var context =
                    assertInstanceOf(FileTriggerContext.class, arguments.triggerContext());
            assertEquals("recovered.txt", context.fileName());
        }
    }

    @Test
    void failingTriggerDoesNotBlockAnotherMatchingTrigger() throws Exception {
        final var failedSubmissions = new LinkedBlockingQueue<String>();
        final var successfulSubmissions = new LinkedBlockingQueue<String>();
        final var trigger =
                new FileTrigger(
                        directory.toString(), Pattern.compile(".*"), FileChangeEvent.ENTRY_CREATED);
        try (final var service = new FileTriggerService(JavaNioFileWatcher::new)) {
            service.registerFileTrigger(
                    0,
                    trigger,
                    arguments -> {
                        final var context =
                                assertInstanceOf(
                                        FileTriggerContext.class, arguments.triggerContext());
                        failedSubmissions.add(context.fileName());
                        throw new CannotCreateTransactionException("Database unavailable");
                    });
            service.registerFileTrigger(
                    1,
                    trigger,
                    arguments -> {
                        final var context =
                                assertInstanceOf(
                                        FileTriggerContext.class, arguments.triggerContext());
                        successfulSubmissions.add(context.fileName());
                    });
            for (final var name : new String[] {"first.txt", "later.txt"}) {
                Files.createFile(directory.resolve(name));
                assertEquals(name, failedSubmissions.poll(10, TimeUnit.SECONDS));
                assertEquals(name, successfulSubmissions.poll(10, TimeUnit.SECONDS));
            }
        }
    }
}
