package ch.ubique.backend.job.controller;

import static ch.ubique.backend.db.data.sort.PageableUtil.toPageableWithDefaultDirection;

import ch.ubique.backend.job.exception.ManualTriggerNotAllowedException;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.client.*;
import ch.ubique.backend.job.model.client.info.JobSystemInfoResponse;
import ch.ubique.backend.job.model.client.triggers.JobTriggers;
import ch.ubique.backend.job.model.response.files.JobRunFileListResponse;
import ch.ubique.backend.job.model.response.StringResponse;
import ch.ubique.backend.job.model.response.SuccessResponse;
import ch.ubique.backend.job.service.controller.JobControllerService;

import jakarta.validation.Valid;

import org.springframework.context.annotation.Profile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
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
            @RequestParam(required = false, defaultValue = "25") final int perPage,
            @RequestParam(required = false, defaultValue = "id") final List<String> orderBy,
            @RequestParam(required = false, defaultValue = "ASC")
                    final List<Sort.Direction> directions)
            throws ClassNotFoundException {
        // pre v1.5 does not send page and pageSize parameter, need to fall back to unpaged
        final var pageable =
                Optional.ofNullable(page)
                        .map(p -> toPageableWithDefaultDirection(p, perPage, orderBy, directions))
                        .orElseGet(Pageable::unpaged);
        return jobControllerService.findJobs(pageable);
    }

    @GetMapping("/runs")
    public JobRunInfosContainer jobRuns(
            @RequestParam(required = false, defaultValue = "0") final int page,
            @RequestParam(required = false, defaultValue = "25") final int perPage,
            @RequestParam(required = false, defaultValue = "id") final List<String> orderBy,
            @RequestParam(required = false, defaultValue = "DESC")
                    final List<Sort.Direction> directions,
            @RequestParam(required = false, defaultValue = "") final Collection<String> includeJobs,
            @RequestParam(required = false, defaultValue = "") final Collection<String> excludeJobs,
            @RequestParam(required = false) final Instant from,
            @RequestParam(required = false) final Instant to,
            @RequestParam(required = false) final JobRunState state)
            throws ClassNotFoundException {
        return jobControllerService.findJobRuns(
                toPageableWithDefaultDirection(page, perPage, orderBy, directions),
                state,
                includeJobs,
                excludeJobs,
                from,
                to);
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
    public ResponseEntity<StringResponse> startJob(
            @PathVariable final String jobIdOrClassName,
            @RequestParam(required = false, defaultValue = "false") final boolean forceRun)
            throws ClassNotFoundException {
        try {
            jobControllerService.startJobManual(jobIdOrClassName, forceRun);
            return ResponseEntity.ok(new StringResponse("Job successfully started"));
        } catch (final ManualTriggerNotAllowedException e) {
            return ResponseEntity.badRequest()
                    .body(
                            new StringResponse(
                                    "Job %s cannot be manually started."
                                            .formatted(jobIdOrClassName)));
        }
    }

    @PostMapping("/job/start_all")
    public void startAllJobs(
            @RequestParam(required = false, defaultValue = "false") final boolean forceRun)
            throws ClassNotFoundException {
        jobControllerService.startAllJobs(forceRun);
    }

    @PostMapping("/run/{jobRunId}/cancel")
    public SuccessResponse cancelJobRun(@PathVariable final int jobRunId) {
        return jobControllerService.cancelJobRun(jobRunId);
    }

    @GetMapping("/run/{jobRunId}/files")
    public JobRunFileListResponse listFiles(@PathVariable final int jobRunId) {
        return jobControllerService.listFiles(jobRunId);
    }

    @GetMapping(
            value = "/run/{jobRunId}/files/{path}",
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<InputStreamResource> getFile(
            @PathVariable final int jobRunId, @PathVariable final String path) {
        return jobControllerService
                .getFile(jobRunId, path)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/job/{jobIdOrClassName}/replay_run/{jobRunId}")
    public ResponseEntity<StringResponse> replayRun(
            @PathVariable final String jobIdOrClassName, @PathVariable final int jobRunId)
            throws ClassNotFoundException {
        try {
            jobControllerService.replayJobRun(jobIdOrClassName, jobRunId);
            return ResponseEntity.ok(new StringResponse("Job successfully replayed"));
        } catch (final ManualTriggerNotAllowedException e) {
            return ResponseEntity.badRequest()
                    .body(
                            new StringResponse(
                                    "Job %s cannot be replayed.".formatted(jobIdOrClassName)));
        }
    }

    @PostMapping("/job/{jobIdOrClassName}/replay_run")
    public ResponseEntity<StringResponse> replayRun(@PathVariable final String jobIdOrClassName)
            throws ClassNotFoundException {
        try {
            jobControllerService.replayJobRun(jobIdOrClassName);
            return ResponseEntity.ok(new StringResponse("Job successfully replayed"));
        } catch (final ManualTriggerNotAllowedException e) {
            return ResponseEntity.badRequest()
                    .body(
                            new StringResponse(
                                    "Job %s cannot be replayed".formatted(jobIdOrClassName)));
        }
    }
}
