package ch.ubique.backend.job.storage;

import ch.ubique.backend.storage.annotations.OutputMapping;
import ch.ubique.backend.storage.annotations.Storage;
import ch.ubique.backend.storage.annotations.StorageObject;
import java.io.IOException;
import org.springframework.web.bind.annotation.PathVariable;

@Storage(clientQualifier = "jobRunStorageClient", generateDocumentation = false)
public interface JobRunStorage {

    @OutputMapping(path = "{jobName}/{runId}/{fileName}")
    void saveJobRunInput(
            @StorageObject byte[] output,
            @PathVariable String jobName,
            @PathVariable String runId,
            @PathVariable String fileName)
            throws IOException;
}
