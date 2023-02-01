package ch.ubique.backend.job.highmem;

import ch.ubique.backend.job.BaseJob;
import io.kubernetes.client.openapi.ApiException;
import java.io.IOException;

public interface JobRunner {

    void run(BaseJob job) throws IOException, ApiException;
}
