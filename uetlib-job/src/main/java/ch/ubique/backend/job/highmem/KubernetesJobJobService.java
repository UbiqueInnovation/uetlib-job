package ch.ubique.backend.job.highmem;

import ch.ubique.backend.job.BaseJob;
import org.apache.commons.lang3.NotImplementedException;

public class KubernetesJobJobService implements HighMemoryJobService {

    @Override
    public void run(final BaseJob job) {
        throw new NotImplementedException();
    }
}
