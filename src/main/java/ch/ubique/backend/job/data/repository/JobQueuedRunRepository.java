package ch.ubique.backend.job.data.repository;

import ch.ubique.backend.job.model.entity.JobQueuedRunEntity;
import ch.ubique.backend.job.model.queue.JobRunQueueState;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@Repository
public interface JobQueuedRunRepository extends JpaRepository<JobQueuedRunEntity, Integer> {
    List<JobQueuedRunEntity> findAllByJobId(int jobId);

    @Query(
            """
                    SELECT j
                    FROM JobQueuedRunEntity j
                    INNER JOIN JobEntity job ON job.id = j.jobId
                    WHERE NOT EXISTS
                    (
                        SELECT 1
                        FROM JSON_TABLE(
                            j.taints,
                            '$[*]' COLUMNS (
                                taint String PATH '$'
                            )
                        ) jt
                        WHERE jt.taint NOT IN :instanceTaints
                    )
                    ORDER BY job.priority DESC
                    """)
    Stream<JobQueuedRunEntity> findByTaints(Set<String> instanceTaints, final Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query(
            """
                    UPDATE JobQueuedRunEntity j
                    SET j.queueState = :newState, j.runner = :runner
                    WHERE j.jobQueuedRunId = :id AND j.queueState = :previousState
                    """)
    int setState(int id, JobRunQueueState previousState, JobRunQueueState newState, String runner);
}
