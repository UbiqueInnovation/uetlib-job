package ch.ubique.backend.job.files;

import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.model.JobRun;
import java.util.Collection;

public interface JobWithFiles extends Job {

    Collection<JobFile> jobRunFiles(JobRun jobRun);
}
