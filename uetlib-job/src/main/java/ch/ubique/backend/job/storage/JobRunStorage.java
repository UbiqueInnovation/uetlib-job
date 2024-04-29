package ch.ubique.backend.job.storage;

import ch.ubique.backend.storage.annotations.*;

import org.springframework.web.bind.annotation.PathVariable;

import java.io.IOException;
import java.io.InputStream;

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

    @DeleteMapping(path = "{jobName}/{runId}", recursive = true)
    boolean deleteJobRunDirectory(@PathVariable String jobName, @PathVariable String runId)
            throws IOException;

    @OutputMapping(path = "{jobName}/{runId}/state.json")
    void saveJobRunState(
            @StorageObject Object output, @PathVariable String jobName, @PathVariable String runId)
            throws IOException;

    @InputMapping(path = "{jobName}/{runId}/state.json")
    InputStream loadJobRunState(@PathVariable String jobName, @PathVariable String runId)
            throws IOException;

    @ExistsMapping(path = "{jobName}/{runId}/state.json")
    boolean jobRunStateExists(@PathVariable String jobName, @PathVariable String runId)
            throws IOException;
}
