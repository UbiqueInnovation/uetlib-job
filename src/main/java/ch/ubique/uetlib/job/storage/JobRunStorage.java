package ch.ubique.uetlib.job.storage;

import ch.ubique.uetlib.storage.annotations.*;
import ch.ubique.uetlib.storage.output.Output;
import ch.ubique.uetlib.storage.spring.annotations.Storage;
import java.io.IOException;
import java.io.InputStream;

@Storage(
        clientQualifier = "jobRunStorageClient",
        codeGenerationType = CodeGenerationType.IMPLEMENTATION)
public interface JobRunStorage {

    @OutputMapping(path = "{jobName}/{runId}/{fileName}")
    void saveJobRunInput(
            @StorageObject Output output,
            @StoragePathVariable String jobName,
            @StoragePathVariable String runId,
            @StoragePathVariable String fileName)
            throws IOException;

    @InputMapping(path = "{jobName}/{runId}/{fileName}")
    InputStream loadJobRunInput(
            @StoragePathVariable String jobName,
            @StoragePathVariable String runId,
            @StoragePathVariable String fileName)
            throws IOException;

    @ExistsMapping(path = "{jobName}/{runId}/{fileName}")
    boolean jobRunInputExists(
            @StoragePathVariable String jobName,
            @StoragePathVariable String runId,
            @StoragePathVariable String fileName)
            throws IOException;

    @DeleteMapping(path = "{jobName}/{runId}", recursive = true)
    boolean deleteJobRunDirectory(
            @StoragePathVariable String jobName, @StoragePathVariable String runId)
            throws IOException;
}
