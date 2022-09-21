package ch.ubique.backend.job.data.jpa.repository;

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

    List<JobRunEntity> findAllByStateAndFinishedAtIsNull(final JobRunState state);

    Optional<JobRunEntity> findFirstByJobIdOrderByStartedAtDesc(Integer jobId);

    Optional<JobRunEntity> findFirstByJobIdAndStateOrderByStartedAtDesc(
            Integer jobId, final JobRunState state);

    Optional<JobRunEntity> findFirstByJobIdAndStateInOrderByStartedAtDesc(
            Integer jobId, final Collection<JobRunState> states);

    Page<JobRunEntity> findAllByJobIdAndStateOrderByStartedAtDesc(
            Integer jobId, JobRunState state, Pageable pageable);

    Page<JobRunEntity> findAllByJobIdOrderByStartedAtDesc(Integer jobId, Pageable pageable);

    Page<JobRunEntity> findAllByStateOrderByStartedAtDesc(JobRunState state, Pageable pageable);

    Page<JobRunEntity> findAllByOrderByStartedAtDesc(Pageable pageable);
}
