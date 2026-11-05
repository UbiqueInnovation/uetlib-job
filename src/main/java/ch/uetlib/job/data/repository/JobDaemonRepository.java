package ch.uetlib.job.data.repository;

import ch.uetlib.job.model.entity.JobDaemonEntity;

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
public interface JobDaemonRepository extends JpaRepository<JobDaemonEntity, Integer> {

    Optional<JobDaemonEntity> findByDaemonName(String daemonName);

    List<JobDaemonEntity> findByUpdatedAtAfter(Instant staleBefore);

    Optional<JobDaemonEntity> findFirstByPrimarySlotIsNotNull();

    /**
     * Share-locks the holder's row until the transaction ends, so the lease can't move meanwhile.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<JobDaemonEntity> findByDaemonNameAndPrimarySlotIsNotNull(String daemonName);

    /**
     * Frees the lease from a holder that stopped checking in or started before {@code daemonName}.
     */
    @Modifying(clearAutomatically = true)
    @Query(
            """
                    UPDATE JobDaemonEntity i
                    SET i.primarySlot = null, i.primarySince = null
                    WHERE i.primarySlot IS NOT NULL
                      AND i.daemonName <> :daemonName
                      AND (i.updatedAt < :staleBefore
                           OR i.startedAt < :startedAt
                           OR (i.startedAt = :startedAt AND i.daemonName < :daemonName))
                    """)
    int releaseOutrankedPrimaryLease(String daemonName, Instant startedAt, Instant staleBefore);

    /** Fails with a unique violation if another instance holds the lease. */
    @Modifying(clearAutomatically = true)
    @Query(
            """
                    UPDATE JobDaemonEntity i
                    SET i.primarySlot = 1, i.primarySince = :now
                    WHERE i.daemonName = :daemonName AND i.updatedAt >= :staleBefore
                    """)
    int acquirePrimaryLease(String daemonName, Instant now, Instant staleBefore);

    @Modifying(clearAutomatically = true)
    @Query(
            """
                    UPDATE JobDaemonEntity i
                    SET i.primarySlot = null, i.primarySince = null
                    WHERE i.daemonName = :daemonName AND i.primarySlot IS NOT NULL
                    """)
    int releasePrimaryLease(String daemonName);

    @Modifying(clearAutomatically = true)
    @Query(
            """
                    DELETE FROM JobDaemonEntity i
                    WHERE i.updatedAt < :staleBefore
                    """)
    int deleteStale(Instant staleBefore);

    @Modifying(clearAutomatically = true)
    @Query(
            """
                    DELETE FROM JobDaemonEntity i
                    WHERE i.daemonName = :daemonName
                    """)
    int deleteByDaemonName(String daemonName);
}
