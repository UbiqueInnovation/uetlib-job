package ch.ubique.backend.job.storage;

import ch.ubique.backend.storage.annotations.*;
import ch.ubique.backend.storage.spring.annotations.Storage;

import java.io.IOException;
import java.io.InputStream;

@Storage(clientQualifier = "jobRunStorageClient", codeGenerationType = CodeGenerationType.ALL)
public interface JobRunStorage {

    @OutputMapping(path = "{jobName}/{runId}/{fileName}.gz")
    void saveJobRunInput(
            @StorageObject byte[] output,
            @StoragePathVariable String jobName,
            @StoragePathVariable String runId,
            @StoragePathVariable String fileName)
            throws IOException;

    @DeleteMapping(path = "{jobName}/{runId}", recursive = true)
    boolean deleteJobRunDirectory(
            @StoragePathVariable String jobName, @StoragePathVariable String runId)
            throws IOException;

    @OutputMapping(path = "{jobName}/{runId}/state.json")
    void saveJobRunState(
            @StorageObject Object output,
            @StoragePathVariable String jobName,
            @StoragePathVariable String runId)
            throws IOException;

    @InputMapping(path = "{jobName}/{runId}/state.json")
    InputStream loadJobRunState(
            @StoragePathVariable String jobName, @StoragePathVariable String runId)
            throws IOException;

    @ExistsMapping(path = "{jobName}/{runId}/state.json")
    boolean jobRunStateExists(
            @StoragePathVariable String jobName, @StoragePathVariable String runId)
            throws IOException;
}
