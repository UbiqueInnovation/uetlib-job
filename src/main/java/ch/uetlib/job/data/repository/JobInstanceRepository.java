package ch.uetlib.job.data.repository;

import ch.uetlib.job.model.entity.JobInstanceEntity;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * {@code primarySlot} is the primary lease: only written here, and {@code UNIQUE}, so never held
 * twice.
 */
@Repository
public interface JobInstanceRepository extends JpaRepository<JobInstanceEntity, Integer> {

    Optional<JobInstanceEntity> findByInstanceName(String instanceName);

    List<JobInstanceEntity> findByUpdatedAtAfter(Instant staleBefore);

    Optional<JobInstanceEntity> findFirstByPrimarySlotIsNotNull();

    /**
     * Share-locks the holder's row until the transaction ends, so the lease can't move meanwhile.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<JobInstanceEntity> findByInstanceNameAndPrimarySlotIsNotNull(String instanceName);

    /**
     * Frees the lease from a holder that stopped checking in or started before {@code
     * instanceName}.
     */
    @Modifying(clearAutomatically = true)
    @Query(
            """
                    UPDATE JobInstanceEntity i
                    SET i.primarySlot = null, i.primarySince = null
                    WHERE i.primarySlot IS NOT NULL
                      AND i.instanceName <> :instanceName
                      AND (i.updatedAt < :staleBefore
                           OR i.startedAt < :startedAt
                           OR (i.startedAt = :startedAt AND i.instanceName < :instanceName))
                    """)
    int releaseOutrankedPrimaryLease(String instanceName, Instant startedAt, Instant staleBefore);

    /** Fails with a unique violation if another instance holds the lease. */
    @Modifying(clearAutomatically = true)
    @Query(
            """
                    UPDATE JobInstanceEntity i
                    SET i.primarySlot = 1, i.primarySince = :now
                    WHERE i.instanceName = :instanceName AND i.updatedAt >= :staleBefore
                    """)
    int acquirePrimaryLease(String instanceName, Instant now, Instant staleBefore);

    @Modifying(clearAutomatically = true)
    @Query(
            """
                    UPDATE JobInstanceEntity i
                    SET i.primarySlot = null, i.primarySince = null
                    WHERE i.instanceName = :instanceName AND i.primarySlot IS NOT NULL
                    """)
    int releasePrimaryLease(String instanceName);

    @Modifying(clearAutomatically = true)
    @Query(
            """
                    DELETE FROM JobInstanceEntity i
                    WHERE i.updatedAt < :staleBefore
                    """)
    int deleteStale(Instant staleBefore);

    @Modifying(clearAutomatically = true)
    @Query(
            """
                    DELETE FROM JobInstanceEntity i
                    WHERE i.instanceName = :instanceName
                    """)
    int deleteByInstanceName(String instanceName);
}
