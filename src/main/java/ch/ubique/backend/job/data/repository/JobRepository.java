package ch.ubique.backend.job.data.repository;

import ch.ubique.backend.job.model.entity.JobEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface JobRepository extends JpaRepository<JobEntity, Integer> {
    @Modifying
    @Query("update JobEntity j set j.enabled = :enabled where j.id = :jobId")
    void setEnabled(int jobId, boolean enabled);
}
