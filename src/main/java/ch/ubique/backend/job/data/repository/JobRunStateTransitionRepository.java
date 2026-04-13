package ch.ubique.backend.job.data.repository;

import ch.ubique.backend.job.model.entity.JobRunStateTransitionEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.stream.Stream;

@Repository
public interface JobRunStateTransitionRepository
        extends JpaRepository<JobRunStateTransitionEntity, Integer> {

    Stream<JobRunStateTransitionEntity> findAllByTransitionedAtAfterOrderByTransitionedAt(
            Instant transitionedAtAfter);

    Stream<JobRunStateTransitionEntity> findAllByJobRunId(int jobRunId);

    JobRunStateTransitionEntity findFirstByJobRunIdOrderByTransitionedAtDesc(int jobRunId);
}
