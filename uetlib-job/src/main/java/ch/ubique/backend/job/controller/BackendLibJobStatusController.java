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

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

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
            @RequestParam(required = false) final Integer jobId,
            @RequestParam(required = false) final JobRunState state)
            throws ClassNotFoundException {
        return jobControllerService.findJobRuns(
                page,
                perPage,
                jobId == null ? null : Collections.singleton(jobId),
                state,
                Collections.emptyList(),
                Collections.emptyList());
    }

    @GetMapping("/runs/include/{idOrClassName}")
    public JobRunInfosContainer jobRunsIncluding(
            @RequestParam(required = false, defaultValue = "0") final int page,
            @RequestParam(required = false, defaultValue = "25") final int perPage,
            @PathVariable final String idOrClassName,
            @RequestParam(required = false) final JobRunState state)
            throws ClassNotFoundException {
        return jobControllerService.findJobRunsIncluding(page, perPage, idOrClassName, state);
    }

    @GetMapping("/runs/exclude/{idOrClassName}")
    public JobRunInfosContainer jobRunsExcluding(
            @RequestParam(required = false, defaultValue = "0") final int page,
            @RequestParam(required = false, defaultValue = "25") final int perPage,
            @PathVariable final String idOrClassName,
            @RequestParam(required = false) final JobRunState state)
            throws ClassNotFoundException {
        return jobControllerService.findJobRunsExcluding(page, perPage, idOrClassName, state);
    }

    @GetMapping("/info")
    public JobSystemInfoResponse info() {
        return jobControllerService.getInfo();
    }

    @GetMapping("/run/{jobRunId}")
    public JobRunInfoContainer jobRun(@PathVariable final int jobRunId) {
        return jobControllerService.findJobRun(jobRunId);
    }

    @GetMapping("/job/{idOrClassName}/properties")
    public Collection<JobPropertyInfo> getProperties(@PathVariable final String idOrClassName)
            throws ClassNotFoundException {
        return jobControllerService.getJobProperties(idOrClassName);
    }

    @PostMapping("/job/{idOrClassName}/properties")
    public Collection<JobPropertyInfo> updateProperties(
            @PathVariable final String idOrClassName,
            @RequestBody final Map<String, String> updatedValues)
            throws ClassNotFoundException {
        return jobControllerService.updateJobProperties(idOrClassName, updatedValues);
    }

    @GetMapping("/job/{idOrClassName}/triggers")
    public JobTriggers getTriggers(@PathVariable final String idOrClassName)
            throws ClassNotFoundException {
        return jobControllerService.getJobTriggers(idOrClassName);
    }

    @PostMapping("/job/{idOrClassName}/triggers")
    public JobTriggers updateTriggers(
            @PathVariable final String idOrClassName,
            @RequestBody @Valid final JobTriggers updatedValues)
            throws ClassNotFoundException {
        return jobControllerService.updateJobTriggers(idOrClassName, updatedValues);
    }

    @PostMapping("/job/{idOrClassName}/start")
    public void startJob(
            @PathVariable final String idOrClassName,
            @RequestParam(required = false, defaultValue = "false") final boolean forceRun)
            throws ClassNotFoundException {
        jobControllerService.startJob(idOrClassName, forceRun);
    }

    @PostMapping("/job/start_all")
    public void startAllJobs(
            @RequestParam(required = false, defaultValue = "false") final boolean forceRun)
            throws ClassNotFoundException {
        jobControllerService.startAllJobs(forceRun);
    }
}
