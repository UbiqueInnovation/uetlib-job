package ch.ubique.backend.job.data.repository;

import ch.ubique.backend.job.model.entity.JobRunEntity;
import ch.ubique.backend.job.model.entity.JobRunLatestStateViewEntity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface JobRunRepository extends JpaRepository<JobRunEntity, Integer> {

    Optional<JobRunEntity> findFirstByJobIdOrderByStartedAtDesc(int jobId);

    Optional<JobRunEntity> findById(int id);

    Page<JobRunEntity> findAllByJobIdAndFinishedAtBeforeOrderByFinishedAt(
            int jobId, Instant finishedBefore, Pageable pageable);

    @Query(
            "SELECT r FROM JobRunLatestStateViewEntity r WHERE r.jobId = :jobId AND (r.finishedAt < :finishedBefore OR (r.latestState = 'SKIPPED' AND r.finishedAt < :skippedBefore))")
    Page<JobRunLatestStateViewEntity> findAllByJobWhereFinishedAtBeforeOrSkippedBefore(
            int jobId, Instant finishedBefore, Instant skippedBefore, Pageable pageable);

    void deleteAllByJobId(int jobId);
}
