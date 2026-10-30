package ch.ubique.uetlib.job.data.repository;

import ch.ubique.uetlib.job.model.JobRunState;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public interface JobRunRepositoryCustom {

    Map<Integer, Set<JobRunState>> findJobRunStates(final Collection<Integer> jobs);

    Map<Integer, Set<JobRunState>> findJobRunStatesSince(final Map<Integer, Instant> jobs);
}
