package ch.ubique.backend.job.model.arguments;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Min;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TestDatabaseTriggerJobRunArguments(
        @Min(0) @DecimalMax(value = "1", inclusive = false) double randomTestValue) {}
