package ch.ubique.backend.job.data.repository;

import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.entity.JobRunLatestStateViewEntity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class JobRunLatestStateViewRepositoryCustomImpl
        implements JobRunLatestStateViewRepositoryCustom {

    @PersistenceContext private EntityManager entityManager;

    @Override
    public Map<Integer, Set<JobRunState>> findJobRunStates(Collection<Integer> jobs) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();

        Root<JobRunLatestStateViewEntity> jrlsv = query.from(JobRunLatestStateViewEntity.class);
        Path<Integer> jobId = jrlsv.get("jobId");
        Path<JobRunState> latestState = jrlsv.get("latestState");

        var jobIdInJobs = cb.in(jobId);
        for (var job : jobs) {
            jobIdInJobs.value(job);
        }
        query.select(cb.tuple(jobId, latestState)).where(jobIdInJobs).groupBy(jobId, latestState);

        var tuples = entityManager.createQuery(query).getResultList();
        return toMapOfSets(jobs, tuples);
    }

    @Override
    public Map<Integer, Set<JobRunState>> findJobRunStatesSince(Map<Integer, Instant> jobs) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();

        Root<JobRunLatestStateViewEntity> jrlsv = query.from(JobRunLatestStateViewEntity.class);
        Path<Integer> jobId = jrlsv.get("jobId");
        Path<Instant> startedAt = jrlsv.get("startedAt");
        Path<JobRunState> latestState = jrlsv.get("latestState");

        List<Predicate> orPredicates = new ArrayList<>();
        for (var entry : jobs.entrySet()) {
            orPredicates.add(
                    cb.and(
                            cb.equal(jobId, entry.getKey()),
                            cb.greaterThanOrEqualTo(startedAt, entry.getValue())));
        }
        query.select(cb.tuple(jobId, latestState))
                .where(cb.or(orPredicates))
                .groupBy(jobId, latestState);

        var tuples = entityManager.createQuery(query).getResultList();
        return toMapOfSets(jobs.keySet(), tuples);
    }

    private Map<Integer, Set<JobRunState>> toMapOfSets(
            Collection<Integer> jobs, List<Tuple> jobIdAndJobRunStateTuples) {

        Map<Integer, Set<JobRunState>> result =
                jobs.stream().collect(Collectors.toMap(id -> id, id -> new HashSet<>()));
        for (var jobIdAndRunState : jobIdAndJobRunStateTuples) {
            var statesForJob = result.get(jobIdAndRunState.get(0, Integer.class));
            statesForJob.add(jobIdAndRunState.get(1, JobRunState.class));
        }
        return result;
    }
}
