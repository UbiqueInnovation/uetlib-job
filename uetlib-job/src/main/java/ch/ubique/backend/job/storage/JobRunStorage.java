package ch.ubique.backend.job.storage;

import ch.ubique.backend.storage.annotations.*;

import org.springframework.web.bind.annotation.PathVariable;

import java.io.IOException;

@Storage(
        clientQualifier = "jobRunStorageClient",
        codeGenerationType = CodeGenerationType.IMPLEMENTATION)
public interface JobRunStorage {

    @OutputMapping(path = "{jobName}/{runId}/{fileName}.gz")
    void saveJobRunInput(
            @StorageObject byte[] output,
            @PathVariable String jobName,
            @PathVariable String runId,
            @PathVariable String fileName)
            throws IOException;

    @DeleteMapping(path = "{jobName}/{runId}/{fileName}.gz")
    boolean deleteJobRunInput(
            @PathVariable String jobName, @PathVariable String runId, @PathVariable String fileName)
            throws IOException;

    @DeleteMapping(path = "{jobName}/{runId}", recursive = true)
    boolean deleteJobRunInputDirectory(@PathVariable String jobName, @PathVariable String runId)
            throws IOException;
}
