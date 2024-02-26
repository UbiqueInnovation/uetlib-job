package ch.ubique.backend.job.service.triggers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import static java.util.Map.entry;
import static java.util.Map.ofEntries;
import static java.util.stream.Collectors.*;

import ch.ubique.backend.job.model.FileTriggerContext;
import ch.ubique.backend.job.model.triggers.FileChangeEvent;
import ch.ubique.backend.job.model.triggers.FileTrigger;
import ch.ubique.backend.job.model.triggers.FileTriggerMatchResult;

import org.apache.commons.collections4.bag.HashBag;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@SpringBootTest(classes = FileTriggerServiceTestConfig.class)
class FileTriggerServiceTest {

    @Autowired private FileTriggerService fileTriggerService;

    @SpyBean private FileTriggerWatcher fileTriggerWatcher;

    @BeforeEach
    void beforeEach() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @SuppressWarnings("java:S2925")
    void testFilesTriggered() throws IOException, InterruptedException {
        final var triggered = new HashBag<FileTrigger>();
        final var tempDir = Files.createTempDirectory(null);

        final var anyFileCreated =
                new FileTrigger(
                        tempDir.toString(), Pattern.compile(".*"), FileChangeEvent.ENTRY_CREATED);
        final var testTxtFileDeleted =
                new FileTrigger(
                        tempDir.toString(),
                        Pattern.compile("(?<fileName>test)(?<fileExtension>\\.txt)"),
                        FileChangeEvent.ENTRY_DELETED);

        final var registeredFileTriggerCaptor =
                ArgumentCaptor.forClass(RegisteredFileTrigger.class);
        final var fileTriggerContextCaptor = ArgumentCaptor.forClass(FileTriggerContext.class);

        fileTriggerService.addTrigger(
                0, anyFileCreated, jobRunArguments -> triggered.add(anyFileCreated));
        fileTriggerService.addTrigger(
                1, testTxtFileDeleted, jobRunArguments -> triggered.add(testTxtFileDeleted));

        // CREATE FILES

        final var test = Files.createFile(tempDir.resolve("test.txt"));
        final var foo = Files.createFile(tempDir.resolve("foo.txt"));
        final var bar = Files.createFile(tempDir.resolve("bar.txt"));

        Thread.sleep(Duration.ofSeconds(3));

        // TEST INVOCATIONS

        verify(fileTriggerWatcher, times(3))
                .consumeJobRunArgument(
                        registeredFileTriggerCaptor.capture(), fileTriggerContextCaptor.capture());

        var registeredFileTriggerCaptorValues = registeredFileTriggerCaptor.getAllValues();
        assertEquals(3, registeredFileTriggerCaptorValues.size());
        assertEquals(anyFileCreated, registeredFileTriggerCaptorValues.get(0).fileTrigger());
        assertEquals(anyFileCreated, registeredFileTriggerCaptorValues.get(1).fileTrigger());
        assertEquals(anyFileCreated, registeredFileTriggerCaptorValues.get(2).fileTrigger());

        var fileTriggerContextCaptorValues = fileTriggerContextCaptor.getAllValues();
        assertEquals(3, fileTriggerContextCaptorValues.size());
        assertInstanceOf(FileTriggerContext.class, fileTriggerContextCaptorValues.get(0));
        assertInstanceOf(FileTriggerContext.class, fileTriggerContextCaptorValues.get(1));
        assertInstanceOf(FileTriggerContext.class, fileTriggerContextCaptorValues.get(2));

        assertEquals(
                Set.of(
                        Pair.of(
                                new FileTriggerMatchResult(List.of("test.txt"), Map.of()),
                                Map.of()),
                        Pair.of(new FileTriggerMatchResult(List.of("foo.txt"), Map.of()), Map.of()),
                        Pair.of(
                                new FileTriggerMatchResult(List.of("bar.txt"), Map.of()),
                                Map.of())),
                fileTriggerContextCaptorValues.stream()
                        .map(
                                fileTriggerContext ->
                                        Pair.of(
                                                fileTriggerContext.fileTriggerMatchResult(),
                                                fileTriggerContext
                                                        .fileTriggerMatchResult()
                                                        .namedGroups()))
                        .collect(toSet()));

        // DELETE FILES

        Files.delete(test);
        Files.delete(foo);
        Files.delete(bar);

        Thread.sleep(Duration.ofSeconds(3));

        // TEST INVOCATIONS

        verify(fileTriggerWatcher, times(4))
                .consumeJobRunArgument(
                        registeredFileTriggerCaptor.capture(), fileTriggerContextCaptor.capture());

        // only the new values
        registeredFileTriggerCaptorValues =
                registeredFileTriggerCaptor.getAllValues().stream()
                        .filter(Predicate.not(registeredFileTriggerCaptorValues::contains))
                        .toList();
        assertEquals(1, registeredFileTriggerCaptorValues.size());
        assertEquals(
                testTxtFileDeleted, registeredFileTriggerCaptorValues.getFirst().fileTrigger());

        // only the new values
        fileTriggerContextCaptorValues =
                fileTriggerContextCaptor.getAllValues().stream()
                        .filter(Predicate.not(fileTriggerContextCaptorValues::contains))
                        .toList();
        assertEquals(1, fileTriggerContextCaptorValues.size());
        assertInstanceOf(FileTriggerContext.class, fileTriggerContextCaptorValues.getFirst());

        assertEquals(
                Set.of(
                        Pair.of(
                                new FileTriggerMatchResult(
                                        List.of("test.txt", "test", ".txt"),
                                        ofEntries(
                                                entry("fileName", "test"),
                                                entry("fileExtension", ".txt"))),
                                ofEntries(
                                        entry("fileName", "test"),
                                        entry("fileExtension", ".txt")))),
                fileTriggerContextCaptorValues.stream()
                        .map(
                                fileTriggerContext ->
                                        Pair.of(
                                                fileTriggerContext.fileTriggerMatchResult(),
                                                fileTriggerContext
                                                        .fileTriggerMatchResult()
                                                        .namedGroups()))
                        .collect(toSet()));

        assertEquals(
                ofEntries(entry("fileName", "test"), entry("fileExtension", ".txt")),
                fileTriggerContextCaptorValues.getFirst().fileTriggerMatchResult().namedGroups());

        assertEquals(3, triggered.getCount(anyFileCreated));
        assertEquals(1, triggered.getCount(testTxtFileDeleted));

        FileUtils.deleteDirectory(tempDir.toFile());
    }
}
