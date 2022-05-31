package ch.ubique.backend.job.data.repository;

import ch.ubique.backend.job.model.JobState;
import ch.ubique.backend.job.model.entity.JobRunEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JobRunRepository extends JpaRepository<JobRunEntity, Integer> {

    List<JobRunEntity> findAllByStateAndFinishedAtIsNull(final JobState state);

    Optional<JobRunEntity> findFirstByJobIdOrderByStartedAtDesc(Integer jobId);

    Page<JobRunEntity> findAllByJobIdAndStateOrderByStartedAtDesc(
            Integer jobId, JobState state, Pageable pageable);

    Page<JobRunEntity> findAllByJobIdOrderByStartedAtDesc(Integer jobId, Pageable pageable);

    Page<JobRunEntity> findAllByStateOrderByStartedAtDesc(JobState state, Pageable pageable);

    Page<JobRunEntity> findAllByOrderByStartedAtDesc(Pageable pageable);
}
