package ch.ubique.backend.job.data.repository;

import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.entity.JobRunLatestStateViewEntity;

import jakarta.validation.constraints.NotEmpty;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;

@Repository
public interface JobRunLatestStateViewRepository
        extends JpaRepository<JobRunLatestStateViewEntity, Integer>,
                JpaSpecificationExecutor<JobRunLatestStateViewEntity> {

    @Query(
            """
                    select jrlsve
                    from JobRunLatestStateViewEntity jrlsve
                    where jrlsve.jobId = :jobId
                      and jrlsve.latestState = :latestState
                    order by jrlsve.startedAt desc
                    limit 1""")
    Optional<JobRunLatestStateViewEntity> findFirstByJobIdAndLatestStateOrderByStartedAtDesc(
            int jobId, JobRunState latestState);

    @Query(
            """
                    select jrlsve
                    from JobRunLatestStateViewEntity jrlsve
                    where jrlsve.jobId = :jobId
                      and jrlsve.latestState in :latestStates
                    order by jrlsve.startedAt desc
                    limit 1""")
    Optional<JobRunLatestStateViewEntity> findFirstByJobIdAndLatestStateInOrderByStartedAtDesc(
            int jobId, Collection<JobRunState> latestStates);

    @Query(
            """
            select jrlsve
            from JobRunLatestStateViewEntity jrlsve
            where jrlsve.latestState in :latestStates""")
    Stream<JobRunLatestStateViewEntity> findAllByLatestStateIn(
            Collection<JobRunState> latestStates);

    @Query(
            """
                select r
                from JobRunLatestStateViewEntity r
                         join (select max(rl.id) as latest_id
                               from JobRunLatestStateViewEntity rl
                               group by rl.jobId) latest on r.id = latest.latest_id
                where r.latestState in :states""")
    Stream<JobRunLatestStateViewEntity> findLatestRunsForEachJobIfInState(
            @NotEmpty Collection<JobRunState> states);

    @Query(
            """
                    select jrlsve
                    from JobRunLatestStateViewEntity jrlsve
                    where jrlsve.jobId = :jobId
                      and jrlsve.latestState = :latestState
                      and jrlsve.finishedAt is not null
                    order by jrlsve.finishedAt desc
                    limit 1""")
    Optional<JobRunLatestStateViewEntity>
            findFirstByJobIdAndLatestStateAndFinishedAtIsNotNullOrderByFinishedAtDesc(
                    int jobId, JobRunState latestState);
}
