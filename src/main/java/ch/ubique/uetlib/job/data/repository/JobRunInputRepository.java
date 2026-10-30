package ch.ubique.uetlib.job.data.repository;

import ch.ubique.uetlib.job.model.JobRunInput;
import ch.ubique.uetlib.job.model.entity.JobRunInputEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface JobRunInputRepository extends JpaRepository<JobRunInputEntity, Integer> {

    List<JobRunInput> findAllByRunId(final Integer runId);

    boolean existsByRunId(final Integer runId);

    @Query("select distinct i.runId from JobRunInputEntity i where i.runId in :runIds")
    List<Integer> findJobRunIdsWithInputs(final Collection<Integer> runIds);
}
