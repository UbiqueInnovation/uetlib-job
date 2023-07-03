package ch.ubique.backend.job.data.repository;

import ch.ubique.backend.job.model.entity.LoggingEventEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface LoggingEventRepository extends JpaRepository<LoggingEventEntity, Long> {

    @Transactional(readOnly = true)
    @Query(
            value =
                    """
                            select le.* from logging_event le
                            join logging_event_property lep on le.event_id = lep.event_id
                            where lep.mapped_key = 'jobRunId' and lep.mapped_value = :id
                            order by le.timestmp""",
            nativeQuery = true)
    List<LoggingEventEntity> findByJobRunId(String id);

    @Transactional
    @Modifying
    @Query(
            value =
                    """
                    DELETE
                    FROM logging_event
                    WHERE event_id IN
                          (SELECT event_id
                           FROM logging_event_property
                           WHERE mapped_key = 'jobRunId' AND mapped_value = cast(:jobRunId AS varchar))""",
            nativeQuery = true)
    void deleteLogsForJobRunId(final int jobRunId);
}
