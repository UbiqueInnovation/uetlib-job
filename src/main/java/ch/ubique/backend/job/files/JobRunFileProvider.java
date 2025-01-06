package ch.ubique.backend.job.files;

import ch.ubique.backend.job.model.JobRun;
import java.util.Collection;

public interface JobRunFileProvider {

    Collection<JobFile> jobRunFiles(JobRun jobRun);
}
