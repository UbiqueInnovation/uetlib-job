package ch.ubique.backend.job.controller;

import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.client.*;
import ch.ubique.backend.job.model.client.info.JobSystemInfoResponse;
import ch.ubique.backend.job.model.client.triggers.JobTriggers;
import ch.ubique.backend.job.service.controller.JobControllerService;

import jakarta.validation.Valid;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@Validated
@Profile("!job-run")
@RequestMapping("/v1/status")
@CrossOrigin(
        value = {
            "https://status.systems.ubique.ch",
            "https://status-dev.systems.ubique.ch",
            "http://localhost:3000"
        },
        allowedHeaders = {"Authorization", "Content-Type"})
public class BackendLibJobStatusController {

    private final JobControllerService jobControllerService;

    public BackendLibJobStatusController(final JobControllerService jobControllerService) {
        this.jobControllerService = jobControllerService;
    }

    @GetMapping
    public ResponseEntity<Void> up() {
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/jobs")
    public JobInfosContainer jobs(
            @RequestParam(required = false) final Integer page,
            @RequestParam(required = false, defaultValue = "25") final int perPage)
            throws ClassNotFoundException {
        return jobControllerService.findJobs(page, perPage);
    }

    @GetMapping("/runs")
    public JobRunInfosContainer jobRuns(
            @RequestParam(required = false, defaultValue = "0") final int page,
            @RequestParam(required = false, defaultValue = "25") final int perPage,
            @RequestParam(required = false, defaultValue = "") final Collection<String> includeJobs,
            @RequestParam(required = false, defaultValue = "") final Collection<String> excludeJobs,
            @RequestParam(required = false) final JobRunState state)
            throws ClassNotFoundException {
        return jobControllerService.findJobRuns(page, perPage, state, includeJobs, excludeJobs);
    }

    @GetMapping("/info")
    public JobSystemInfoResponse info() {
        return jobControllerService.getInfo();
    }

    @GetMapping("/run/{jobRunId}")
    public JobRunInfoContainer jobRun(@PathVariable final int jobRunId)
            throws ClassNotFoundException {
        return jobControllerService.findJobRun(jobRunId);
    }

    @GetMapping("/job/{jobIdOrClassName}/properties")
    public Collection<JobPropertyInfo> getProperties(@PathVariable final String jobIdOrClassName)
            throws ClassNotFoundException {
        return jobControllerService.getJobProperties(jobIdOrClassName);
    }

    @PostMapping("/job/{jobIdOrClassName}/properties")
    public Collection<JobPropertyInfo> updateProperties(
            @PathVariable final String jobIdOrClassName,
            @RequestBody final Map<String, String> updatedValues)
            throws ClassNotFoundException {
        return jobControllerService.updateJobProperties(jobIdOrClassName, updatedValues);
    }

    @GetMapping("/job/{jobIdOrClassName}/triggers")
    public JobTriggers getTriggers(@PathVariable final String jobIdOrClassName)
            throws ClassNotFoundException {
        return jobControllerService.getJobTriggers(jobIdOrClassName);
    }

    @PostMapping("/job/{jobIdOrClassName}/triggers")
    public JobTriggers updateTriggers(
            @PathVariable final String jobIdOrClassName,
            @RequestBody @Valid final JobTriggers updatedValues)
            throws ClassNotFoundException {
        return jobControllerService.updateJobTriggers(jobIdOrClassName, updatedValues);
    }

    @PostMapping("/job/{jobIdOrClassName}/start")
    public void startJob(
            @PathVariable final String jobIdOrClassName,
            @RequestParam(required = false, defaultValue = "false") final boolean forceRun)
            throws ClassNotFoundException {
        jobControllerService.startJobManual(jobIdOrClassName, forceRun);
    }

    @PostMapping("/job/start_all")
    public void startAllJobs(
            @RequestParam(required = false, defaultValue = "false") final boolean forceRun)
            throws ClassNotFoundException {
        jobControllerService.startAllJobs(forceRun);
    }
}
