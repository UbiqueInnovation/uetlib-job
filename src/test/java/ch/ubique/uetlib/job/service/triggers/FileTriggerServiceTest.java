package ch.ubique.uetlib.job.service.triggers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import static java.util.Map.entry;
import static java.util.Map.ofEntries;
import static java.util.stream.Collectors.*;

import ch.ubique.uetlib.job.model.FileTriggerContext;
import ch.ubique.uetlib.job.model.triggers.FileChangeEvent;
import ch.ubique.uetlib.job.model.triggers.FileTrigger;

import org.apache.commons.collections4.bag.HashBag;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

class FileTriggerServiceTest {

    @Test
    @SuppressWarnings("java:S2925")
    void testFilesTriggered() throws IOException, InterruptedException {

        final var fileTriggerService = Mockito.spy(new FileTriggerService());

        final var triggered = new ArrayList<FileTrigger>();
        final var fileTriggerContexts = new ArrayList<FileTriggerContext>();
        final var tempDir = Files.createTempDirectory(null);

        final var anyFileCreated =
                new FileTrigger(
                        tempDir.toString(), Pattern.compile(".*"), FileChangeEvent.ENTRY_CREATED);
        final var testTxtFileDeleted =
                new FileTrigger(
                        tempDir.toString(),
                        Pattern.compile("(?<fileName>test)(?<fileExtension>\\.txt)"),
                        FileChangeEvent.ENTRY_DELETED);

        fileTriggerService.addTrigger(
                0,
                anyFileCreated,
                null,
                jobRunArguments -> {
                    triggered.add(anyFileCreated);
                    fileTriggerContexts.add((FileTriggerContext) jobRunArguments.triggerContext());
                });
        fileTriggerService.addTrigger(
                1,
                testTxtFileDeleted,
                null,
                jobRunArguments -> {
                    triggered.add(testTxtFileDeleted);
                    fileTriggerContexts.add((FileTriggerContext) jobRunArguments.triggerContext());
                });

        // CREATE FILES

        final var test = Files.createFile(tempDir.resolve("test.txt"));
        final var foo = Files.createFile(tempDir.resolve("foo.txt"));
        final var bar = Files.createFile(tempDir.resolve("bar.txt"));

        Thread.sleep(Duration.ofSeconds(3));

        // TEST INVOCATIONS

        var registeredFileTriggerCaptorValues = triggered.stream().toList();
        assertEquals(3, triggered.size());
        assertEquals(anyFileCreated, triggered.getFirst());
        assertEquals(anyFileCreated, triggered.get(1));
        assertEquals(anyFileCreated, triggered.get(2));

        var fileTriggerContextCaptorValues = fileTriggerContexts.stream().toList();
        assertEquals(3, fileTriggerContextCaptorValues.size());
        assertInstanceOf(FileTriggerContext.class, fileTriggerContextCaptorValues.get(0));
        assertInstanceOf(FileTriggerContext.class, fileTriggerContextCaptorValues.get(1));
        assertInstanceOf(FileTriggerContext.class, fileTriggerContextCaptorValues.get(2));

        assertEquals(
                Set.of(
                        Triple.of(0, List.of("test.txt"), Map.of()),
                        Triple.of(0, List.of("foo.txt"), Map.of()),
                        Triple.of(0, List.of("bar.txt"), Map.of())),
                fileTriggerContextCaptorValues.stream()
                        .map(
                                fileTriggerContext ->
                                        Triple.of(
                                                fileTriggerContext.matchResult().groupCount(),
                                                fileTriggerContext.matchResult().groups(),
                                                fileTriggerContext
                                                        .matchResult()
                                                        .namedGroupValues()))
                        .collect(toSet()));

        // DELETE FILES

        Files.delete(test);
        Files.delete(foo);
        Files.delete(bar);

        Thread.sleep(Duration.ofSeconds(3));

        // TEST INVOCATIONS

        // only the new values
        registeredFileTriggerCaptorValues =
                triggered.stream()
                        .filter(Predicate.not(registeredFileTriggerCaptorValues::contains))
                        .toList();
        assertEquals(1, registeredFileTriggerCaptorValues.size());
        assertEquals(testTxtFileDeleted, registeredFileTriggerCaptorValues.getFirst());

        // only the new values
        fileTriggerContextCaptorValues =
                fileTriggerContexts.stream()
                        .filter(Predicate.not(fileTriggerContextCaptorValues::contains))
                        .toList();
        assertEquals(1, fileTriggerContextCaptorValues.size());
        assertInstanceOf(FileTriggerContext.class, fileTriggerContextCaptorValues.getFirst());

        assertEquals(
                Set.of(
                        Triple.of(
                                2,
                                List.of("test.txt", "test", ".txt"),
                                ofEntries(
                                        entry("fileName", "test"),
                                        entry("fileExtension", ".txt")))),
                fileTriggerContextCaptorValues.stream()
                        .map(
                                fileTriggerContext ->
                                        Triple.of(
                                                fileTriggerContext.matchResult().groupCount(),
                                                fileTriggerContext.matchResult().groups(),
                                                fileTriggerContext
                                                        .matchResult()
                                                        .namedGroupValues()))
                        .collect(toSet()));

        assertEquals(
                ofEntries(entry("fileName", "test"), entry("fileExtension", ".txt")),
                fileTriggerContextCaptorValues.getFirst().matchResult().namedGroupValues());

        final var bag = new HashBag<FileTrigger>();
        bag.addAll(triggered);
        assertEquals(3, bag.getCount(anyFileCreated));
        assertEquals(1, bag.getCount(testTxtFileDeleted));

        FileUtils.deleteDirectory(tempDir.toFile());
    }
}
