package ch.ubique.backend.job.data.repository;

import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.entity.JobRunEntity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

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
}
