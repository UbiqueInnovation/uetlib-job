package ch.ubique.backend.job.data.repository;

import ch.ubique.backend.job.model.JobRunInput;
import ch.ubique.backend.job.model.entity.JobRunInputEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface JobRunInputRepository extends JpaRepository<JobRunInputEntity, Integer> {

    List<JobRunInput> findAllByRunId(final Integer runId);

    boolean existsByRunId(final Integer runId);

    @Query("select distinct i.runId from JobRunInputEntity i where i.runId in :runIds")
    List<Integer> findJobRunIdsWithInputs(final Collection<Integer> runIds);
}
