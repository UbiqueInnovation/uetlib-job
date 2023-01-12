package ch.ubique.backend.job.highmem;

import ch.ubique.backend.job.BaseJob;

public interface HighMemoryJobService {

    void run(BaseJob job);
}
