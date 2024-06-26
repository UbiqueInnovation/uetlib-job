package ch.ubique.backend.job.data.repository;

import ch.ubique.backend.job.model.entity.JobRunEntity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface JobRunRepository extends JpaRepository<JobRunEntity, Integer> {

    Optional<JobRunEntity> findFirstByJobIdOrderByStartedAtDesc(int jobId);

    Optional<JobRunEntity> findById(int id);

    Page<JobRunEntity> findAllByJobIdAndFinishedAtBeforeOrderByFinishedAt(
            int jobId, Instant finishedBefore, Pageable pageable);

    void deleteAllByJobId(int jobId);
}
