package ch.ubique.backend.job.data.repository;

import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.entity.JobRunEntity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Repository
public interface JobRunRepository extends JpaRepository<JobRunEntity, Integer> {

    List<JobRun> findAllByStateAndFinishedAtIsNull(final JobRunState state);

    Optional<JobRun> findFirstByJobIdOrderByStartedAtDesc(int jobId);

    Optional<JobRun> findFirstByJobIdAndStateOrderByStartedAtDesc(
            int jobId, final JobRunState state);

    Optional<JobRun> findFirstByJobIdAndStateInOrderByStartedAtDesc(
            int jobId, final Collection<JobRunState> states);

    Page<JobRun> findAllByJobIdAndStateOrderByStartedAtDesc(
            int jobId, JobRunState state, Pageable pageable);

    Page<JobRun> findAllByJobIdOrderByStartedAtDesc(int jobId, Pageable pageable);

    Page<JobRun> findAllByStateOrderByStartedAtDesc(JobRunState state, Pageable pageable);

    Page<JobRun> findAllByOrderByStartedAtDesc(Pageable pageable);

    Optional<JobRun> findById(int id);

    @Query(
            value =
                    """
                                        SELECT r_outer.pk_job_run_id, r_outer.fk_job_id, r_outer.state, r_outer.started_at, r_outer.finished_at, r_outer.job_version
                                        FROM t_job_run r_outer
                                        WHERE r_outer.finished_at + (:daysToKeep * INTERVAL '1 day') < (
                                            SELECT finished_at
                                            FROM t_job_run r_inner
                                            WHERE r_inner.state = 'DONE' AND r_inner.fk_job_id = :jobId
                                            ORDER BY r_inner.finished_at DESC
                                            LIMIT 1
                                        ) AND r_outer.fk_job_id = :jobId
                                        ORDER BY r_outer.finished_at ASC
                                        LIMIT :chunkSize
                            """,
            nativeQuery = true)
    Stream<JobRunEntity> findAllByJobIdAndFinishedAt(int jobId, int daysToKeep, int chunkSize);
}
