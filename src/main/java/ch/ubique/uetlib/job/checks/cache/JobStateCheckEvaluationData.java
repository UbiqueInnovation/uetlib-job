package ch.ubique.uetlib.job.checks.cache;

import ch.ubique.uetlib.job.model.JobClassAndId;
import ch.ubique.uetlib.job.model.JobRunState;
import ch.ubique.uetlib.job.model.properties.enums.StateCheckStrategy;

import java.util.Map;
import java.util.Set;

/**
 * The relevant job run state information to evaluate the JobStateCheck for different error states.
 *
 * @param jobStateCheckStrategy StateCheckStrategy for each Job
 * @param latestState The latest job run state for jobs with CHECK_LATEST_SUCCESSFUL
 * @param problemStates All distinct problematic job run states for jobs with CHECK_ALL_SUCCESSFUL
 * @param statesInInterval All distinct job run states for runs within the configured check interval
 *     (STATE_CHECK_INTERVAL) for jobs with CHECK_INTERVAL_ALL_SUCCESSFUL
 */
public record JobStateCheckEvaluationData(
        Map<JobClassAndId, StateCheckStrategy> jobStateCheckStrategy,
        Map<JobClassAndId, JobRunState> latestState,
        Map<JobClassAndId, Set<JobRunState>> problemStates,
        Map<JobClassAndId, Set<JobRunState>> statesInInterval) {}
