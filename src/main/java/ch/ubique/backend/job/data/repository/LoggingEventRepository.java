package ch.ubique.backend.job.data.repository;

import ch.ubique.backend.job.model.entity.LoggingEventEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

@Repository
public interface LoggingEventRepository extends JpaRepository<LoggingEventEntity, Long> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    void deleteAllByJobRunId(int jobRunId);
}
