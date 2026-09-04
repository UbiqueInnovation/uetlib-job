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
     * Never deletes a {@code BaseStateJob} continuation, which would strand its run; callers must
     * also exclude {@code RUNNING}.
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
     * Only ever deletes a {@code QUEUED} row; a claimed one already has a runner, so cancel its job
     * run instead.
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
     * Sends a {@code BaseStateJob} continuation's own row back to {@code QUEUED} for its target
     * runner.
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
    int resetStaleClaimsForJobs(
            Collection<Integer> jobIds,
            JobRunQueueState staleState,
            JobRunQueueState resetState,
            Instant staleBefore,
            Instant now);

    /**
     * Cancels {@code QUEUED} rows idle since {@code staleBefore}; a row an instance waits to run is
     * touched ({@link #touchQueuedRun}), so it survives.
     */
    @Modifying(clearAutomatically = true)
    @Query(
            """
                    UPDATE JobQueuedRunEntity j
                    SET j.queueState = ch.uetlib.job.model.queue.JobRunQueueState.CANCELLED, j.updatedAt = :now
                    WHERE j.jobId IN :jobIds AND j.queueState = ch.uetlib.job.model.queue.JobRunQueueState.QUEUED
                      AND j.updatedAt < :staleBefore
                    """)
    int expireStaleQueuedRunsForJobs(Collection<Integer> jobIds, Instant staleBefore, Instant now);

    /** Marks a still-{@code QUEUED} row as wanted by an instance waiting to run it. */
    @Modifying(clearAutomatically = true)
    @Query(
            """
                    UPDATE JobQueuedRunEntity j
                    SET j.updatedAt = :now
                    WHERE j.jobQueuedRunId = :id
                      AND j.queueState = ch.uetlib.job.model.queue.JobRunQueueState.QUEUED
                    """)
    int touchQueuedRun(int id, Instant now);

    @Modifying(clearAutomatically = true)
    @Query(
            """
                    DELETE FROM JobQueuedRunEntity j
                    WHERE j.jobId IN :jobIds AND j.queueState = ch.uetlib.job.model.queue.JobRunQueueState.CANCELLED
                      AND j.updatedAt < :retainAfter
                    """)
    int deleteOldCancelledQueuedRunsForJobs(Collection<Integer> jobIds, Instant retainAfter);

    /**
     * For rows whose job this build doesn't know; {@code knownJobIds} must not be empty, as {@code
     * NOT IN ()} is invalid SQL.
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
     * Unconditional reset to {@code QUEUED}, restoring {@code jobRunId} as it was before the claim.
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
