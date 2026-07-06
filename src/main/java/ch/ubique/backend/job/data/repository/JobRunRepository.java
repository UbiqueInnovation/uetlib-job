package ch.ubique.backend.job.data.repository;

import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.entity.JobLatestFinishedRunEntity;
import ch.ubique.backend.job.model.entity.JobLatestRunEntity;
import ch.ubique.backend.job.model.entity.JobRunEntity;

import jakarta.validation.constraints.NotEmpty;

import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;

@Repository
public interface JobRunRepository
        extends JpaRepository<JobRunEntity, Integer>,
                JpaSpecificationExecutor<JobRunEntity>,
                JobRunRepositoryCustom {

    Optional<JobRunEntity> findFirstByJobIdOrderByStartedAtDesc(int jobId);

    @Query("select r from JobLatestRunEntity r where r.jobId in :jobIds")
    Collection<JobLatestRunEntity> findLatestRuns(Collection<Integer> jobIds);

    @Query("select r from JobLatestFinishedRunEntity r where r.jobId in :jobIds")
    Collection<JobLatestFinishedRunEntity> findLatestFinishedRuns(Collection<Integer> jobIds);

    Optional<JobRunEntity> findById(int id);

    Page<JobRunEntity> findAllByJobIdAndFinishedAtBeforeOrderByFinishedAt(
            int jobId, Instant finishedBefore, Pageable pageable);

    @Query(
            "SELECT r FROM JobRunEntity r WHERE r.jobId = :jobId AND (r.finishedAt < :finishedBefore OR (r.latestState = 'SKIPPED' AND r.finishedAt < :skippedBefore))")
    Page<JobRunEntity> findAllByJobWhereFinishedAtBeforeOrSkippedBefore(
            int jobId, Instant finishedBefore, Instant skippedBefore, Pageable pageable);

    Optional<JobRunEntity> findFirstByJobIdAndLatestStateOrderByStartedAtDesc(
            int jobId, JobRunState latestState);

    @Query(
            """
                select run
                from JobRunEntity run
                where run.jobId = :jobId
                  and run.latestState in :latestState
                  and run.startedAt >= :after
                order by run.startedAt desc
                limit 1""")
    Optional<JobRunEntity> findAnyJobRunInStateSince(
            int jobId, Collection<JobRunState> latestState, Instant after);

    @Query(
            """
                select run
                from JobRunEntity run
                where run.latestState in :latestStates""")
    Stream<JobRunEntity> findAllByLatestStateIn(Collection<JobRunState> latestStates);

    @Query("select r from JobLatestRunEntity r where r.latestState in :states")
    Stream<JobLatestRunEntity> findLatestRunsForEachJobIfInState(
            @NotEmpty Collection<JobRunState> states);

    @Query(
            """
                select run
                from JobRunEntity run
                where run.jobId = :jobId
                  and run.latestState = :latestState
                  and run.finishedAt is not null
                order by run.finishedAt desc
                limit 1""")
    Optional<JobRunEntity> findFirstByJobIdAndLatestStateOrderByFinishedAtDesc(
            int jobId, JobRunState latestState);

    Collection<JobRunEntity> findAllByJobIdInAndLatestState(
            Collection<Integer> jobIds, JobRunState state);

    Map<Integer, Set<JobRunState>> findJobRunStates(final Collection<Integer> jobs);

    Map<Integer, Set<JobRunState>> findJobRunStatesSince(final Map<Integer, Instant> jobs);
}
