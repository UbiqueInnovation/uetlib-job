package ch.ubique.uetlib.job.model.client.info;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record JobSystemInfo(
        @JsonProperty(required = true) @NotNull @Valid JobSystemCronInfo cron,
        @JsonProperty(required = true) @NotNull @Valid
                List<JobSystemRunnerGroupInfo> runnerGroups) {}
