package ch.uetlib.job.data.repository;

import ch.uetlib.job.logging.LoggingEvent;
import ch.uetlib.job.model.entity.LoggingEventEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoggingEventRepository extends JpaRepository<LoggingEventEntity, Long> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    void deleteAllByJobRunId(int jobRunId);

    Optional<LoggingEventEntity> findFirstByJobRunIdOrderByTimestampDesc(int jobRunId);

    @Query(
            "select l.timestamp, l.formattedMessage from LoggingEventEntity l where l.jobRunId = :jobRunId order by l.timestamp asc offset :offset")
    List<LoggingEvent> findByJobRunIdOrderByTimestampAsc(
            @Param("jobRunId") int jobRunId, @Param("offset") int offset);
}
