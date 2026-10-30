package ch.ubique.uetlib.job.controller;

import static ch.ubique.uetlib.db.data.sort.PageableUtil.toPageableWithDefaultDirection;

import ch.ubique.uetlib.job.exception.ManualTriggerNotAllowedException;
import ch.ubique.uetlib.job.model.JobRunState;
import ch.ubique.uetlib.job.model.client.*;
import ch.ubique.uetlib.job.model.client.info.JobSystemInfoResponse;
import ch.ubique.uetlib.job.model.client.triggers.JobTriggers;
import ch.ubique.uetlib.job.model.executer.JobSubmissionResult;
import ch.ubique.uetlib.job.model.response.StringResponse;
import ch.ubique.uetlib.job.model.response.SuccessResponse;
import ch.ubique.uetlib.job.model.response.files.JobRunFileListResponse;
import ch.ubique.uetlib.job.service.controller.JobControllerService;
import ch.ubique.uetlib.spring.validation.ValidateResponse;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@Validated
@ValidateResponse
@Profile("!job-run")
@RequestMapping(value = "/v1/status", produces = MediaType.APPLICATION_JSON_VALUE)
@CrossOrigin(
        value = {
            "https://status.systems.ubique.ch",
            "https://status-dev.systems.ubique.ch",
            "http://localhost:3000",
            "http://localhost:3001"
        },
        allowedHeaders = {"Authorization", "Content-Type"})
public class UetlibJobStatusController {

    private final JobControllerService jobControllerService;

    public UetlibJobStatusController(final JobControllerService jobControllerService) {
        this.jobControllerService = jobControllerService;
    }

    @GetMapping
    public ResponseEntity<Void> up() {
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/jobs")
    public JobInfosContainer jobs() throws ClassNotFoundException {
        return jobControllerService.getJobInfos();
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
            @RequestParam(required = false, defaultValue = "") final Collection<JobRunState> state)
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
    public ResponseEntity<JobSubmissionResult> startJob(
            @PathVariable final String jobIdOrClassName,
            @RequestParam(required = false, defaultValue = "false") final boolean forceRun)
            throws ClassNotFoundException {
        final var result = jobControllerService.startJobManual(jobIdOrClassName, forceRun);
        if (result.status().isSuccess()) {
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.badRequest().body(result);
    }

    @PostMapping("/job/{jobIdOrClassName}/enable")
    public ResponseEntity<StringResponse> enable(@PathVariable final String jobIdOrClassName)
            throws ClassNotFoundException {
        jobControllerService.setEnabled(jobIdOrClassName, true);
        return ResponseEntity.ok(new StringResponse("Job enabled"));
    }

    @PostMapping("/job/{jobIdOrClassName}/disable")
    public ResponseEntity<StringResponse> disable(@PathVariable final String jobIdOrClassName)
            throws ClassNotFoundException {
        jobControllerService.setEnabled(jobIdOrClassName, false);
        return ResponseEntity.ok(new StringResponse("Job disabled"));
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

    @PostMapping("/run/{jobRunId}/acknowledge")
    public SuccessResponse acknowledgeJobRun(@PathVariable final int jobRunId) {
        return jobControllerService.acknowledgeJobRun(jobRunId);
    }

    @GetMapping("/run/{jobRunId}/logs")
    public String logs(@PathVariable final int jobRunId) {
        return jobControllerService.logs(jobRunId);
    }

    @GetMapping("/run/{jobRunId}/files")
    public JobRunFileListResponse listFiles(@PathVariable final int jobRunId) {
        return jobControllerService.listFiles(jobRunId);
    }

    @GetMapping("/run/{jobRunId}/files/all")
    public ResponseEntity<StreamingResponseBody> getAllFiles(@PathVariable final int jobRunId) {
        return jobControllerService
                .getAllFiles(jobRunId)
                .map(
                        b ->
                                ResponseEntity.ok()
                                        .header(
                                                HttpHeaders.CONTENT_DISPOSITION,
                                                String.format(
                                                        "attachment; filename=files_%d.zip",
                                                        jobRunId))
                                        .body(b))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping(
            value = "/run/{jobRunId}/files/{*path}",
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<InputStreamResource> getFile(
            @PathVariable final int jobRunId, @PathVariable final String path) {
        return jobControllerService
                .getFile(jobRunId, path.replaceFirst("^/", ""))
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
