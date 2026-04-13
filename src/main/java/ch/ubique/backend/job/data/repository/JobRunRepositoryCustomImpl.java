package ch.ubique.backend.job.data.repository;

import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.entity.JobRunEntity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class JobRunRepositoryCustomImpl implements JobRunRepositoryCustom {

    @PersistenceContext private EntityManager entityManager;

    @Override
    public Map<Integer, Set<JobRunState>> findJobRunStates(final Collection<Integer> jobs) {
        final var cb = entityManager.getCriteriaBuilder();
        final var query = cb.createTupleQuery();

        final var jrlsv = query.from(JobRunEntity.class);
        final Path<Integer> jobId = jrlsv.get("jobId");
        final Path<JobRunState> latestState = jrlsv.get("latestState");

        final var jobIdInJobs = cb.in(jobId);
        for (final var job : jobs) {
            jobIdInJobs.value(job);
        }
        query.select(cb.tuple(jobId, latestState)).where(jobIdInJobs).groupBy(jobId, latestState);

        final var tuples = entityManager.createQuery(query).getResultList();
        return toMapOfSets(jobs, tuples);
    }

    @Override
    public Map<Integer, Set<JobRunState>> findJobRunStatesSince(final Map<Integer, Instant> jobs) {
        final var cb = entityManager.getCriteriaBuilder();
        final var query = cb.createTupleQuery();

        final var jrlsv = query.from(JobRunEntity.class);
        final Path<Integer> jobId = jrlsv.get("jobId");
        final Path<Instant> startedAt = jrlsv.get("startedAt");
        final Path<JobRunState> latestState = jrlsv.get("latestState");

        final List<Predicate> orPredicates = new ArrayList<>();
        for (final var entry : jobs.entrySet()) {
            orPredicates.add(
                    cb.and(
                            cb.equal(jobId, entry.getKey()),
                            cb.greaterThanOrEqualTo(startedAt, entry.getValue())));
        }
        query.select(cb.tuple(jobId, latestState))
                .where(cb.or(orPredicates))
                .groupBy(jobId, latestState);

        final var tuples = entityManager.createQuery(query).getResultList();
        return toMapOfSets(jobs.keySet(), tuples);
    }

    private Map<Integer, Set<JobRunState>> toMapOfSets(
            final Collection<Integer> jobs, final List<Tuple> jobIdAndJobRunStateTuples) {

        final Map<Integer, Set<JobRunState>> result =
                jobs.stream().collect(Collectors.toMap(id -> id, id -> new HashSet<>()));
        for (final var jobIdAndRunState : jobIdAndJobRunStateTuples) {
            final var statesForJob = result.get(jobIdAndRunState.get(0, Integer.class));
            statesForJob.add(jobIdAndRunState.get(1, JobRunState.class));
        }
        return result;
    }
}
