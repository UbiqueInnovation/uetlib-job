package ch.ubique.backend.job.data.repository;

import ch.ubique.backend.job.model.entity.LoggingEventEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface LoggingEventRepository extends JpaRepository<LoggingEventEntity, Long> {

    @Transactional(readOnly = true)
    @Query(
            """
    SELECT e FROM LoggingEventEntity e
    JOIN LoggingEventPropertyEntity p ON e.eventId = p.eventId
    WHERE p.mappedKey = "jobRunId" AND p.mappedValue = :id
    ORDER BY timestamp""")
    List<LoggingEventEntity> findByJobRunId(String id);
}
