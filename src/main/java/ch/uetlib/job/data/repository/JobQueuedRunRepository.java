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

    /**
     * Deletes only rows representing a fresh run - never a {@code BaseStateJob} continuation, whose
     * run is already under way; deleting one strands that run in {@code SCHEDULED} with nothing to
     * resume it. Callers must also exclude {@link JobRunQueueState#RUNNING}.
     */
    long deleteByJobIdInAndJobRunIdIsNullAndQueueStateIn(
            Collection<Integer> jobIds, Collection<JobRunQueueState> states);

    /**
     * Deletes a single row only while it is still {@code state} <em>and</em> represents a fresh
     * run, so an admin delete cannot strand an in-flight {@code BaseStateJob} continuation.
     */
    long deleteByJobQueuedRunIdAndJobRunIdIsNullAndQueueState(
            int jobQueuedRunId, JobRunQueueState queueState);

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
                    SET j.queueState = :newState, j.runner = :runner, j.updatedAt = :now
                    WHERE j.jobQueuedRunId = :id AND j.queueState = :previousState
                    """)
    int setState(
            int id,
            JobRunQueueState previousState,
            JobRunQueueState newState,
            String runner,
            Instant now);

    /**
     * Links a claimed row to its run and moves it to {@link JobRunQueueState#RUNNING}. Conditional,
     * so it cannot resurrect a row that is no longer claimed.
     */
    @Modifying(clearAutomatically = true)
    @Query(
            """
                    UPDATE JobQueuedRunEntity j
                    SET j.queueState = ch.uetlib.job.model.queue.JobRunQueueState.RUNNING,
                        j.jobRunId = :jobRunId, j.updatedAt = :now
                    WHERE j.jobQueuedRunId = :id
                      AND j.queueState = ch.uetlib.job.model.queue.JobRunQueueState.CLAIMED
                    """)
    int linkRunAndMarkRunning(int id, int jobRunId, Instant now);

    /**
     * Re-queues the row of a run handing its processing phase to another runner (a {@code
     * BaseStateJob} continuation): same row, same {@code jobRunId}, back to {@link
     * JobRunQueueState#QUEUED} so a poller on the target runner can claim it.
     */
    @Modifying(clearAutomatically = true)
    @Query(
            """
                    UPDATE JobQueuedRunEntity j
                    SET j.queueState = ch.uetlib.job.model.queue.JobRunQueueState.QUEUED,
                        j.runner = null, j.updatedAt = :now
                    WHERE j.jobRunId = :jobRunId
                      AND j.queueState = ch.uetlib.job.model.queue.JobRunQueueState.RUNNING
                    """)
    int requeueRunningRunForReprocessing(int jobRunId, Instant now);

    /**
     * Marks a single still-{@link JobRunQueueState#QUEUED} row {@link JobRunQueueState#CANCELLED},
     * unconditionally - unlike the time-based {@link #expireStaleQueuedRunsForJob}.
     */
    @Modifying(clearAutomatically = true)
    @Query(
            """
                    UPDATE JobQueuedRunEntity j
                    SET j.queueState = ch.uetlib.job.model.queue.JobRunQueueState.CANCELLED, j.updatedAt = :now
                    WHERE j.jobQueuedRunId = :id
                      AND j.queueState = ch.uetlib.job.model.queue.JobRunQueueState.QUEUED
                    """)
    int cancelQueuedRunById(int id, Instant now);

    @Modifying(clearAutomatically = true)
    @Query(
            """
                    UPDATE JobQueuedRunEntity j
                    SET j.queueState = :resetState, j.runner = null, j.updatedAt = :now
                    WHERE j.jobId IN :jobIds AND j.queueState = :staleState AND j.updatedAt < :staleBefore
                    """)
    int resetStaleClaimsForJob(
            int jobId,
            JobRunQueueState staleState,
            JobRunQueueState resetState,
            Instant staleBefore,
            Instant now);

    /**
     * Marks every {@link JobRunQueueState#QUEUED} row of {@code jobIds} as {@link
     * JobRunQueueState#CANCELLED} - unclaimable (see {@code QUEUE_TIMEOUT}) - if its {@code
     * updatedAt} is before {@code staleBefore}. Every claim/reset cycle bumps {@code updatedAt}, so
     * a row still being actively (if unsuccessfully) retried isn't cancelled out from under it.
     */
    @Modifying(clearAutomatically = true)
    @Query(
            """
                    UPDATE JobQueuedRunEntity j
                    SET j.queueState = ch.uetlib.job.model.queue.JobRunQueueState.CANCELLED, j.updatedAt = :now
                    WHERE j.jobId = :jobId AND j.queueState = ch.uetlib.job.model.queue.JobRunQueueState.QUEUED
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
                    WHERE j.jobId IN :jobIds AND j.queueState = ch.uetlib.job.model.queue.JobRunQueueState.CANCELLED
                      AND j.updatedAt < :retainAfter
                    """)
    int deleteOldCancelledQueuedRunsForJob(int jobId, Instant retainAfter);

    /**
     * Deletes {@link JobRunQueueState#CANCELLED} rows past {@code retainAfter} whose job is not in
     * {@code knownJobIds} - rows {@link #deleteOldCancelledQueuedRunsForJob} can never reach.
     * {@code knownJobIds} must not be empty: an empty {@code NOT IN} list is invalid SQL.
     */
    @Modifying(clearAutomatically = true)
    @Query(
            """
                    DELETE FROM JobQueuedRunEntity j
                    WHERE j.queueState = ch.uetlib.job.model.queue.JobRunQueueState.CANCELLED
                      AND j.updatedAt < :retainAfter
                      AND j.jobId NOT IN :knownJobIds
                    """)
    int deleteOldCancelledQueuedRunsForUnknownJobs(
            Collection<Integer> knownJobIds, Instant retainAfter);

    /**
     * Unconditionally resets a single row - for defensive/internal use only. {@code jobRunId} is
     * restored rather than cleared, so releasing a claim puts the row back as it was: {@code null}
     * for a fresh run, the bound run for a {@code BaseStateJob} continuation.
     */
    @Modifying(clearAutomatically = true)
    @Query(
            """
                    UPDATE JobQueuedRunEntity j
                    SET j.queueState = :newState, j.runner = null, j.jobRunId = :jobRunId, j.updatedAt = :now
                    WHERE j.jobQueuedRunId = :id
                    """)
    void forceReset(int id, JobRunQueueState newState, @Nullable Integer jobRunId, Instant now);

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
