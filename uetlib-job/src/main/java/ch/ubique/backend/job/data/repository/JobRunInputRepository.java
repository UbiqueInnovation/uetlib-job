package ch.ubique.backend.job.data.repository;

import ch.ubique.backend.job.model.JobRunInput;
import ch.ubique.backend.job.model.entity.JobRunInputEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface JobRunInputRepository extends JpaRepository<JobRunInputEntity, Integer> {

    List<JobRunInput> findAllByRunId(final Integer runId);

    @Transactional
    @Modifying
    @Query(
            "delete from JobRunInputEntity jri where jri.runId in (select jr.id from JobRunEntity"
                + " jr where jr.jobId = :jobId)")
    void deleteAllByJobId(int jobId);
}
