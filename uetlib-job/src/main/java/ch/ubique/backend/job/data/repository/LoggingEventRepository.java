package ch.ubique.backend.job.data.repository;

import ch.ubique.backend.job.model.entity.LoggingEventEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.stream.Stream;

@Repository
public interface LoggingEventRepository extends JpaRepository<LoggingEventEntity, Long> {

    @Query(
            value =
                    """
                    select *
                    from logging_event
                    where job_run_id = :jobRunId
                    order by timestmp
                    limit :pageSize offset :skipLines""",
            nativeQuery = true)
    Stream<LoggingEventEntity> findByJobRunId(int jobRunId, long skipLines, int pageSize);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from logging_event where job_run_id = :jobRunId", nativeQuery = true)
    void deleteLogsForJobRunId(int jobRunId);
}
