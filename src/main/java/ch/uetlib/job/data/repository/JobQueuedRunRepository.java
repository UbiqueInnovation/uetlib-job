package ch.uetlib.job.data.repository;

import ch.uetlib.job.model.entity.JobQueuedRunEntity;
import ch.uetlib.job.model.queue.JobRunQueueState;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

@Repository
public interface JobQueuedRunRepository
        extends JpaRepository<JobQueuedRunEntity, Integer>,
                JpaSpecificationExecutor<JobQueuedRunEntity> {
    List<JobQueuedRunEntity> findAllByJobId(int jobId);

    List<JobQueuedRunEntity> findAllByQueueState(JobRunQueueState queueState);

    /** Never deletes a {@link JobRunQueueState#RUNNING} row, regardless of its own state. */
    long deleteByJobQueuedRunIdAndQueueStateNot(int jobQueuedRunId, JobRunQueueState excludedState);

    /** Never deletes a {@link JobRunQueueState#RUNNING} row: callers must exclude it from {@code states}. */
    long deleteByJobIdInAndQueueStateIn(
            Collection<Integer> jobIds, Collection<JobRunQueueState> states);

    Optional<JobQueuedRunEntity> findFirstByOrderByCreatedAtAsc();

    Optional<JobQueuedRunEntity> findFirstByOrderByCreatedAtDesc();

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
                    ORDER BY COALESCE(j.priority, job.priority) DESC
                    """)
    Stream<JobQueuedRunEntity> findByTaints(Set<String> instanceTaints, final Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query(
            """
                    UPDATE JobQueuedRunEntity j
                    SET j.queueState = :newState, j.runner = :runner, j.lastHeartbeat = :now
                    WHERE j.jobQueuedRunId = :id AND j.queueState = :previousState
                    """)
    int setState(
            int id,
            JobRunQueueState previousState,
            JobRunQueueState newState,
            String runner,
            Instant now);

    /**
     * Reclaims rows that were claimed but never progressed to {@link JobRunQueueState#RUNNING}
     * (e.g. the claiming instance died before submitting) - detected via a stale heartbeat, not
     * {@code updatedAt}, since {@code @Modifying} queries bypass {@code @UpdateTimestamp}.
     */
    @Modifying(clearAutomatically = true)
    @Query(
            """
                    UPDATE JobQueuedRunEntity j
                    SET j.queueState = :resetState, j.runner = null, j.lastHeartbeat = null
                    WHERE j.queueState = :staleState AND j.lastHeartbeat < :staleBefore
                    """)
    int resetStaleClaims(
            JobRunQueueState staleState, JobRunQueueState resetState, Instant staleBefore);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE JobQueuedRunEntity j SET j.lastHeartbeat = :now WHERE j.jobQueuedRunId = :id")
    void touchHeartbeat(int id, Instant now);

    /** Unconditionally resets a single row - for defensive/internal use only. */
    @Modifying(clearAutomatically = true)
    @Query(
            """
                    UPDATE JobQueuedRunEntity j
                    SET j.queueState = :newState, j.runner = null, j.lastHeartbeat = null
                    WHERE j.jobQueuedRunId = :id
                    """)
    void forceReset(int id, JobRunQueueState newState);
}
