package ch.uetlib.job.data.repository;

import ch.uetlib.job.model.entity.JobRunDatabaseSubmissionEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.stream.Stream;

@Repository
public interface JobRunDatabaseSubmissionRepository
        extends JpaRepository<JobRunDatabaseSubmissionEntity, Integer> {
    Stream<JobRunDatabaseSubmissionEntity> findAllByExecutedAtIsNull();

    void deleteByJobId(int jobId);
}
