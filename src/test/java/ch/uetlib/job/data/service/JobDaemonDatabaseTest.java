package ch.uetlib.job.data.service;

import static org.junit.jupiter.api.Assertions.*;

import ch.uetlib.job.data.BaseJobDatabaseTest;
import ch.uetlib.job.data.repository.JobDaemonRepository;
import ch.uetlib.job.model.JobDaemon;
import ch.uetlib.job.model.JobQueuedRun;
import ch.uetlib.job.model.JobRunArguments;
import ch.uetlib.job.model.entity.JobDaemonEntity;
import ch.uetlib.job.model.properties.enums.JobQueueBehavior;
import ch.uetlib.job.model.properties.enums.JobRunnerKey;
import ch.uetlib.job.model.triggers.TriggerType;
import ch.uetlib.job.service.inputs.JobRunInputManager;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Check-ins and the primary lease on every database, whose constraint and locks enforce one
 * primary.
 */
class JobDaemonDatabaseTest extends BaseJobDatabaseTest {

    private static final String VERSION = "1.0.0";
    private static final int TEST_JOB_ID = 1;
    private static final Duration STALE_AFTER = Duration.ofSeconds(90);
    private static final Instant EARLIER =
            Instant.now().minus(Duration.ofHours(1)).truncatedTo(ChronoUnit.SECONDS);
    private static final Instant LATER = EARLIER.plusSeconds(60);

    @Autowired JobDataService jobDataService;
    @Autowired JobDaemonRepository jobDaemonRepository;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean JobRunInputManager jobRunInputManager;

    @Test
    void aCheckInRefreshesTheInstancesRowRatherThanAddingASecondOne() {
        checkIn("pod-a", EARLIER);

        final var refreshed =
                jobDataService.checkInDaemon("pod-a", "2.0.0", Set.of("gpu"), true, 4, EARLIER);

        assertEquals(
                1,
                jobDataService.findLiveDaemons(staleBefore()).stream()
                        .filter(instance -> instance.daemonName().equals("pod-a"))
                        .count());
        assertEquals("2.0.0", refreshed.version());
        assertEquals(Set.of("gpu"), refreshed.taints());
        assertEquals(4, refreshed.triggerCount());
    }

    @Test
    void anInstanceThatStoppedCheckingInIsNoLongerLive() {
        checkIn("pod-a", EARLIER);

        // Nothing checked in since a moment in the future, i.e. everything is stale.
        assertTrue(
                jobDataService.findLiveDaemons(Instant.now().plusSeconds(60)).stream()
                        .noneMatch(instance -> instance.daemonName().equals("pod-a")));
    }

    @Test
    void aPurgedInstanceReinsertsItselfAtItsNextCheckInKeepingItsStartTime() {
        checkIn("pod-a", EARLIER);
        jobDataService.deleteStaleInstances(Instant.now().plusSeconds(60));
        assertTrue(find("pod-a").isEmpty());

        final var reinserted = checkIn("pod-a", EARLIER);

        // Keeping its start time keeps its rank: being purged must not let it outrank - and take
        // the lease from - an instance that started after it.
        assertEquals(EARLIER, reinserted.startedAt());
        assertTrue(find("pod-a").isPresent());
    }

    @Test
    void aCandidateTakesTheLeaseNobodyHolds() {
        checkIn("pod-a", EARLIER);

        assertTrue(takeLease("pod-a", EARLIER));
        assertTrue(find("pod-a").orElseThrow().holdsPrimaryLease());
    }

    @Test
    void theDatabaseRejectsASecondHolder() {
        checkIn("pod-a", EARLIER);
        checkIn("pod-b", LATER);
        takeLease("pod-a", EARLIER);
        final var now = Instant.now();
        final var staleBefore = staleBefore();

        assertThrows(
                DataIntegrityViolationException.class,
                () -> jobDaemonRepository.acquirePrimaryLease("pod-b", now, staleBefore));
    }

    @Test
    void aLiveHolderThatStartedLaterKeepsTheLease() {
        checkIn("pod-a", EARLIER);
        checkIn("pod-b", LATER);
        takeLease("pod-b", LATER);

        assertFalse(takeLease("pod-a", EARLIER));
        assertTrue(find("pod-b").orElseThrow().holdsPrimaryLease());
    }

    @Test
    void aNewerInstanceTakesTheLeaseFromALiveOlderHolder() {
        checkIn("pod-a", EARLIER);
        takeLease("pod-a", EARLIER);
        checkIn("pod-b", LATER);

        assertTrue(takeLease("pod-b", LATER));
        assertFalse(find("pod-a").orElseThrow().holdsPrimaryLease());
    }

    @Test
    void aHolderThatStoppedCheckingInLosesTheLeaseEvenToAnOlderInstance() {
        final var anHourAgo = Instant.now().minus(Duration.ofHours(1));
        jobDaemonRepository.save(
                new JobDaemonEntity("pod-b", VERSION, Set.of(), true, 0, LATER, anHourAgo));
        jobDaemonRepository.acquirePrimaryLease(
                "pod-b", anHourAgo, anHourAgo.minus(Duration.ofHours(1)));
        checkIn("pod-a", EARLIER);

        assertTrue(takeLease("pod-a", EARLIER));
        assertFalse(find("pod-b").orElseThrow().holdsPrimaryLease());
    }

    @Test
    void onlyTheHolderQueuesFromATrigger() {
        checkIn("pod-a", EARLIER);
        takeLease("pod-a", EARLIER);
        checkIn("pod-b", LATER);

        assertTrue(queueAsPrimary("pod-a").isPresent());
        assertTrue(queueAsPrimary("pod-b").isEmpty());
    }

    @Test
    void aHolderShuttingDownHandsTheLeaseOverSoOnlyTheNewHolderQueues() {
        checkIn("pod-a", EARLIER);
        takeLease("pod-a", EARLIER);
        checkIn("pod-b", LATER);

        jobDataService.removeDaemon("pod-a", "pod-b", staleBefore());

        assertTrue(find("pod-a").isEmpty());
        assertTrue(find("pod-b").orElseThrow().holdsPrimaryLease());
        assertTrue(queueAsPrimary("pod-a").isEmpty());
        assertTrue(queueAsPrimary("pod-b").isPresent());
    }

    @Test
    void anInstanceShuttingDownWithoutTheLeaseCannotMoveIt() {
        checkIn("pod-a", EARLIER);
        checkIn("pod-b", LATER);
        checkIn("pod-c", LATER.plusSeconds(60));
        takeLease("pod-b", LATER);

        jobDataService.removeDaemon("pod-a", "pod-c", staleBefore());

        assertTrue(find("pod-b").orElseThrow().holdsPrimaryLease());
        assertFalse(find("pod-c").orElseThrow().holdsPrimaryLease());
    }

    @Test
    void aNewHolderSeesWhenThePreviousHolderLastQueuedFromAFixedRateTrigger() {
        // What a new primary schedules its fixed-rate triggers from, so a lease move neither
        // repeats the previous holder's run nor waits a full period from the new one's startup.
        checkIn("pod-a", EARLIER);
        takeLease("pod-a", EARLIER);
        final var queued = queueAsPrimary("pod-a", TriggerType.FIXED_RATE).orElseThrow();
        queueAsPrimary("pod-a", TriggerType.CRON).orElseThrow();
        checkIn("pod-b", LATER);
        assertTrue(takeLease("pod-b", LATER));

        // MySQL keeps whole seconds only.
        final var lastQueuedAt =
                jobDataService.findLastQueuedAt(TEST_JOB_ID, TriggerType.FIXED_RATE).orElseThrow();
        assertTrue(
                Duration.between(queued.createdAt(), lastQueuedAt)
                                .abs()
                                .compareTo(Duration.ofSeconds(1))
                        <= 0,
                "last queued at " + lastQueuedAt + ", queued at " + queued.createdAt());
        assertTrue(jobDataService.findLastQueuedAt(TEST_JOB_ID, TriggerType.RETRY).isEmpty());
    }

    @Test
    void theLastFixedRateQueueingIncludesRunsWhoseQueueRowIsGone() {
        final var run =
                jobDataService.createJobRun(
                        TEST_JOB_ID,
                        VERSION,
                        JobRunnerKey.CURRENT,
                        new JobRunArguments(false, TriggerType.FIXED_RATE, null, null, null));

        final var lastQueuedAt =
                jobDataService.findLastQueuedAt(TEST_JOB_ID, TriggerType.FIXED_RATE).orElseThrow();

        assertTrue(
                Duration.between(run.startedAt(), lastQueuedAt)
                                .abs()
                                .compareTo(Duration.ofSeconds(5))
                        < 0,
                "last queued at " + lastQueuedAt + ", run started at " + run.startedAt());
        assertTrue(jobDataService.findLastQueuedAt(TEST_JOB_ID, TriggerType.RETRY).isEmpty());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void fireTimeChecksShareTheirLockWhileMovingTheLeaseWaitsForThem() throws Exception {
        // Committed rows and separate transactions: row locks only matter between transactions.
        final var transaction = new TransactionTemplate(transactionManager);
        final var executor = Executors.newFixedThreadPool(3);
        final var checkInFlight = new CountDownLatch(1);
        final var endCheck = new CountDownLatch(1);
        try {
            transaction.executeWithoutResult(status -> {
                checkIn("lock-a", EARLIER);
                takeLease("lock-a", EARLIER);
            });

            final var firstCheck =
                    executor.submit(() ->
                            transaction.execute(status -> {
                                final var holds = jobDataService.holdsPrimaryLease("lock-a");
                                checkInFlight.countDown();
                                awaitQuietly(endCheck);
                                return holds;
                            }));
            assertTrue(checkInFlight.await(10, TimeUnit.SECONDS));

            // A second check is not blocked by the first one's lock ...
            final var secondCheck =
                    executor.submit(() ->
                            transaction.execute(
                                    status -> jobDataService.holdsPrimaryLease("lock-a")));
            assertEquals(Boolean.TRUE, secondCheck.get(10, TimeUnit.SECONDS));

            // ... but moving the lease is, until the first check's transaction ends.
            final var release =
                    executor.submit(() ->
                            transaction.execute(
                                    status -> jobDaemonRepository.releasePrimaryLease("lock-a")));
            assertThrows(TimeoutException.class, () -> release.get(500, TimeUnit.MILLISECONDS));

            endCheck.countDown();
            assertEquals(Boolean.TRUE, firstCheck.get(10, TimeUnit.SECONDS));
            assertEquals(Integer.valueOf(1), release.get(10, TimeUnit.SECONDS));
            assertEquals(
                    Boolean.FALSE,
                    transaction.execute(status -> jobDataService.holdsPrimaryLease("lock-a")));
        } finally {
            endCheck.countDown();
            executor.shutdownNow();
            transaction.executeWithoutResult(
                    status -> jobDaemonRepository.deleteByDaemonName("lock-a"));
        }
    }

    private JobDaemon checkIn(final String daemonName, final Instant startedAt) {
        return jobDataService.checkInDaemon(daemonName, VERSION, Set.of(), true, 0, startedAt);
    }

    private boolean takeLease(final String daemonName, final Instant startedAt) {
        return jobDataService.takePrimaryLease(daemonName, startedAt, staleBefore());
    }

    private Optional<JobQueuedRun> queueAsPrimary(final String daemonName) {
        return queueAsPrimary(daemonName, null);
    }

    private Optional<JobQueuedRun> queueAsPrimary(
            final String daemonName, final @Nullable TriggerType triggerType) {
        return jobDataService.queueNewRunIfPrimary(
                daemonName,
                TEST_JOB_ID,
                null,
                VERSION,
                new JobRunArguments(false, triggerType, null, null, null),
                Set.of(),
                JobQueueBehavior.ALLOW_MULTIPLE);
    }

    private Optional<JobDaemon> find(final String daemonName) {
        return jobDataService.findLiveDaemons(Instant.EPOCH).stream()
                .filter(instance -> instance.daemonName().equals(daemonName))
                .findFirst();
    }

    private static Instant staleBefore() {
        return Instant.now().minus(STALE_AFTER);
    }

    private static void awaitQuietly(final CountDownLatch latch) {
        try {
            final var _ = latch.await(30, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
