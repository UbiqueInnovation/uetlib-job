package ch.ubique.backend.job.model.client;

import ch.ubique.backend.job.model.Job;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobInfosContainer(
        @NotNull List<JobInfo> jobs, @NotNull Map<Integer, JobRunInfo> latestRuns) {

    public static JobInfosContainer from(
            final List<Job> jobs, final Map<Integer, JobRunInfo> latestRuns) {
        return new JobInfosContainer(jobs.stream().map(JobInfo::from).toList(), latestRuns);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record JobInfo(
            @NotNull int id,
            @NotNull String name,
            @NotNull String clazz,
            String cronExpression,
            @NotNull boolean cronActive,
            @NotNull boolean cronExpressionValid,
            @NotNull boolean cronFixed,
            @NotNull boolean forceRunnable) {

        public static JobInfo from(final Job job) {
            return new JobInfo(
                    job.getId(),
                    job.getName(),
                    job.getClazz(),
                    job.getCronExpression(),
                    job.isCronActive(),
                    job.isCronExpressionValid(),
                    job.isCronFixed(),
                    job.isForceRunnable());
        }
    }
}
