package ch.ubique.backend.job.data.repository;

import ch.ubique.backend.job.model.entity.JobRunDatabaseSubmissionEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobRunDatabaseSubmissionRepository
        extends JpaRepository<JobRunDatabaseSubmissionEntity, Integer> {
    List<JobRunDatabaseSubmissionEntity> findAllBySubmittedAtIsNull();
}
