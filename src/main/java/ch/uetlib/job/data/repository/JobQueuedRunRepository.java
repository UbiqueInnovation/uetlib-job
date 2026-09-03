package ch.uetlib.job.data.repository;

import ch.uetlib.job.model.entity.JobQueuedRunEntity;
import ch.uetlib.job.model.queue.JobRunQueueState;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface JobQueuedRunRepository
        extends JpaRepository<JobQueuedRunEntity, Integer>,
                JpaSpecificationExecutor<JobQueuedRunEntity> {
    List<JobQueuedRunEntity> findAllByJobId(int jobId);

    List<JobQueuedRunEntity> findAllByQueueState(JobRunQueueState queueState);

    List<JobQueuedRunEntity> findByJobIdAndQueueStateInOrderByCreatedAtAsc(
            int jobId, Collection<JobRunQueueState> queueStates);

    Optional<JobQueuedRunEntity> findByJobRunIdAndQueueState(
            int jobRunId, JobRunQueueState queueState);

    /**
     * Only ever deletes a {@link JobRunQueueState#QUEUED} row - once a row is claimed, a runner
     * already has it and may already have a linked job run; deleting the queue row at that point
     * would just orphan that job run instead of stopping it. Cancel the job run instead.
     */
    long deleteByJobQueuedRunIdAndQueueState(int jobQueuedRunId, JobRunQueueState queueState);

    /**
     * Never deletes a {@link JobRunQueueState#RUNNING} row: callers must exclude it from {@code
     * states}.
     */
    long deleteByJobIdInAndQueueStateIn(
            Collection<Integer> jobIds, Collection<JobRunQueueState> states);

    /** Deletes every row in the given state, regardless of job - for bulk acknowledge-all. */
    long deleteByQueueState(JobRunQueueState queueState);

    Optional<JobQueuedRunEntity> findFirstByOrderByCreatedAtAsc();

    Optional<JobQueuedRunEntity> findFirstByOrderByCreatedAtDesc();

    @Query(
            """
                    SELECT q
                    FROM JobQueuedRunEntity q
                    WHERE q.queueState = :state
                    ORDER BY COALESCE(q.priority, 0) DESC, q.createdAt ASC
                    """)
    Slice<JobQueuedRunEntity> findByQueueStateOrderedByPriority(
            JobRunQueueState state, Pageable pageable);

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

    @Modifying(clearAutomatically = true)
    @Query(
            """
                    UPDATE JobQueuedRunEntity j
                    SET j.lastHeartbeat = :now
                    WHERE j.jobRunId = :jobRunId AND j.queueState = ch.uetlib.job.model.queue.JobRunQueueState.RUNNING
                    """)
    void touchHeartbeatByJobRunId(int jobRunId, Instant now);

    @Modifying(clearAutomatically = true)
    @Query(
            """
                    UPDATE JobQueuedRunEntity j
                    SET j.queueState = :resetState, j.runner = null, j.lastHeartbeat = null, j.updatedAt = :now
                    WHERE j.jobId = :jobId AND j.queueState = :staleState
                      AND (j.lastHeartbeat IS NULL OR j.lastHeartbeat < :staleBefore)
                    """)
    int resetStaleClaimsForJob(
            int jobId,
            JobRunQueueState staleState,
            JobRunQueueState resetState,
            Instant staleBefore,
            Instant now);

    /**
     * Marks every {@link JobRunQueueState#QUEUED} row of {@code jobId} as {@link
     * JobRunQueueState#CANCELLED} - unclaimable (see {@code QUEUE_TIMEOUT}) - if its last activity
     * (its {@code lastHeartbeat} if present, else its {@code updatedAt}) is before {@code
     * staleBefore}. The same null-safe idiom as {@link #resetStaleClaimsForJob}: every existing
     * reset path already clears {@code lastHeartbeat}, so in practice this is keyed on {@code
     * updatedAt} for a row currently {@code QUEUED} - each claim/reset cycle restarts the clock, so
     * a row still being actively (if unsuccessfully) retried isn't cancelled out from under it.
     */
    @Modifying(clearAutomatically = true)
    @Query(
            """
                    UPDATE JobQueuedRunEntity j
                    SET j.queueState = ch.uetlib.job.model.queue.JobRunQueueState.CANCELLED, j.updatedAt = :now
                    WHERE j.jobId = :jobId AND j.queueState = ch.uetlib.job.model.queue.JobRunQueueState.QUEUED
                      AND (j.lastHeartbeat IS NULL OR j.lastHeartbeat < :staleBefore)
                      AND j.updatedAt < :staleBefore
                    """)
    int expireStaleQueuedRunsForJob(int jobId, Instant staleBefore, Instant now);

    /**
     * Deletes every {@link JobRunQueueState#CANCELLED} row of {@code jobId} that's been cancelled
     * (its {@code updatedAt}) since before {@code retainAfter} - see {@code
     * CANCELLED_QUEUE_RETENTION}.
     */
    @Modifying(clearAutomatically = true)
    @Query(
            """
                    DELETE FROM JobQueuedRunEntity j
                    WHERE j.jobId = :jobId AND j.queueState = ch.uetlib.job.model.queue.JobRunQueueState.CANCELLED
                      AND j.updatedAt < :retainAfter
                    """)
    int deleteOldCancelledQueuedRunsForJob(int jobId, Instant retainAfter);

    /** Unconditionally resets a single row - for defensive/internal use only. */
    @Modifying(clearAutomatically = true)
    @Query(
            """
                    UPDATE JobQueuedRunEntity j
                    SET j.queueState = :newState, j.runner = null, j.lastHeartbeat = null, j.updatedAt = :now
                    WHERE j.jobQueuedRunId = :id
                    """)
    void forceReset(int id, JobRunQueueState newState, Instant now);

    /**
     * Only ever updates a {@link JobRunQueueState#QUEUED} row's priority: once a row is claimed (or
     * running), a runner already has it, so reordering it is meaningless.
     */
    @Modifying(clearAutomatically = true)
    @Query(
            """
                    UPDATE JobQueuedRunEntity j
                    SET j.priority = :priority
                    WHERE j.jobQueuedRunId = :id AND j.queueState = :allowedState
                    """)
    int updatePriority(int id, @Nullable Integer priority, JobRunQueueState allowedState);
}
