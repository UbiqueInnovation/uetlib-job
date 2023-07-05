package ch.ubique.backend.job.data.repository;

import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.entity.JobRunEntity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface JobRunRepository extends JpaRepository<JobRunEntity, Integer> {

    Collection<JobRunEntity> findAllByFinishedAtIsNull();

    Collection<JobRunEntity> findAllByFinishedAtIsNotNullAndFinishedAtAfter(Instant instant);

    List<JobRunEntity> findAllByStateAndFinishedAtIsNull(final JobRunState state);

    Optional<JobRunEntity> findFirstByJobIdOrderByStartedAtDesc(int jobId);

    Optional<JobRunEntity> findFirstByJobIdAndStateOrderByStartedAtDesc(
            int jobId, final JobRunState state);

    Optional<JobRunEntity> findFirstByJobIdAndStateInOrderByStartedAtDesc(
            int jobId, final Collection<JobRunState> states);

    Page<JobRunEntity> findAllByJobIdAndStateOrderByStartedAtDesc(
            int jobId, JobRunState state, Pageable pageable);

    Page<JobRunEntity> findAllByJobIdOrderByStartedAtDesc(int jobId, Pageable pageable);

    Page<JobRunEntity> findAllByStateOrderByStartedAtDesc(JobRunState state, Pageable pageable);

    Page<JobRunEntity> findAllByOrderByStartedAtDesc(Pageable pageable);

    Optional<JobRunEntity> findById(int id);

    Optional<JobRun> findFirstByStateAndAndJobIdOrderByFinishedAtDesc(
            JobRunState jobRunState, int jobId);

    Page<JobRunEntity> findAllByJobIdAndFinishedAtBeforeOrderByFinishedAt(
            int jobId, Instant finishedBefore, Pageable pageable);
}
