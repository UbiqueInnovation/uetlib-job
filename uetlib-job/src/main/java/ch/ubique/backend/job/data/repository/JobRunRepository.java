package ch.ubique.backend.job.data.repository;

import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.entity.JobRunEntity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

@Repository
public interface JobRunRepository
        extends JpaRepository<JobRunEntity, Integer>, JpaSpecificationExecutor<JobRunEntity> {

    Collection<JobRunEntity> findAllByFinishedAtIsNull();

    Collection<JobRunEntity> findAllByFinishedAtIsNotNullAndFinishedAtAfter(Instant instant);

    Optional<JobRunEntity> findFirstByJobIdOrderByStartedAtDesc(int jobId);

    Optional<JobRunEntity> findFirstByJobIdAndStateOrderByStartedAtDesc(
            int jobId, final JobRunState state);

    Optional<JobRunEntity> findFirstByJobIdAndStateInOrderByStartedAtDesc(
            int jobId, final Collection<JobRunState> states);

    Optional<JobRunEntity> findById(int id);

    Optional<JobRun> findFirstByStateAndFinishedAtIsNotNullAndJobIdOrderByFinishedAtDesc(
            JobRunState jobRunState, int jobId);

    Page<JobRunEntity> findAllByJobIdAndFinishedAtBeforeOrderByFinishedAt(
            int jobId, Instant finishedBefore, Pageable pageable);
}
