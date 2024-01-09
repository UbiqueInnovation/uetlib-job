package ch.ubique.backend.job.data.repository;

import ch.ubique.backend.job.model.entity.LoggingEventEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoggingEventRepository extends JpaRepository<LoggingEventEntity, Long> {

    @Query(
            value =
                    """
                            select le.*
                            from logging_event le
                                join logging_event_property lep on le.event_id = lep.event_id
                            where lep.mapped_key = 'jobRunId' and lep.mapped_value = :jobRunId
                            order by le.timestmp
                            limit :pageSize offset :skipLines""",
            nativeQuery = true)
    List<LoggingEventEntity> findByJobRunId(String jobRunId, long skipLines, int pageSize);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value =
                    """
                            delete
                            from logging_event
                            where event_id in
                                  (select event_id
                                   from logging_event_property
                                   where mapped_key = 'jobRunId' and mapped_value = :jobRunId)""",
            nativeQuery = true)
    void deleteLogsForJobRunId(String jobRunId);
}
