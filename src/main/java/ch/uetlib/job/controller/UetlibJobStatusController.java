package ch.uetlib.job.controller;

import static ch.uetlib.db.data.sort.PageableUtil.toPageableWithDefaultDirection;

import ch.uetlib.job.exception.ManualTriggerNotAllowedException;
import ch.uetlib.job.model.JobRunState;
import ch.uetlib.job.model.client.*;
import ch.uetlib.job.model.client.info.JobSystemInfoResponse;
import ch.uetlib.job.model.client.triggers.JobTriggers;
import ch.uetlib.job.model.queue.JobRunQueueState;
import ch.uetlib.job.model.queue.QueueSubmissionFailure;
import ch.uetlib.job.model.queue.QueueSubmissionResult;
import ch.uetlib.job.model.queue.QueueSubmissionSuccess;
import ch.uetlib.job.model.response.StringResponse;
import ch.uetlib.job.model.response.SuccessResponse;
import ch.uetlib.job.model.response.files.JobRunFileListResponse;
import ch.uetlib.job.service.controller.JobControllerService;
import ch.uetlib.job.service.instance.JobInstanceIdentity;
import ch.uetlib.spring.boot.validation.ValidateResponse;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;

import org.springframework.context.annotation.Profile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.Instant;
import java.util.*;

@RestController
@Validated
@ValidateResponse
@Profile("!job-run & job-primary-triggers")
@RequestMapping(value = "/v1/status", produces = MediaType.APPLICATION_JSON_VALUE)
@CrossOrigin(
        value = {
            "https://status.systems.ubique.ch",
            "https://status-dev.systems.ubique.ch",
            "http://localhost:3000",
            "http://localhost:3001"
        },
        allowedHeaders = {"Authorization", "Content-Type"},
        exposedHeaders = UetlibJobStatusController.INSTANCE_HEADER)
public class UetlibJobStatusController {

    /** Names the instance that served a status response (SCHEDULING.md §9). */
    public static final String INSTANCE_HEADER = "X-Job-Instance";

    private final JobControllerService jobControllerService;
    private final JobInstanceIdentity instanceIdentity;

    public UetlibJobStatusController(
            final JobControllerService jobControllerService,
            final JobInstanceIdentity instanceIdentity) {
        this.jobControllerService = jobControllerService;
        this.instanceIdentity = instanceIdentity;
    }

    /**
     * Runs before every handler here: more than one instance may serve the status API, and the
     * status interface shows which one answered.
     */
    @ModelAttribute
    public void addInstanceHeader(final HttpServletResponse response) {
        response.setHeader(INSTANCE_HEADER, instanceIdentity.name());
    }

    @GetMapping
    public ResponseEntity<Void> up() {
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/jobs")
    public JobInfosContainer jobs() {
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
            @RequestParam(required = false, defaultValue = "")
                    final Collection<JobRunState> state) {
        return jobControllerService.findJobRuns(
                toPageableWithDefaultDirection(page, perPage, orderBy, directions),
                state,
                includeJobs,
                excludeJobs,
                from,
                to);
    }

    @GetMapping("/queue")
    public JobQueueInfosContainer queue(
            @RequestParam(required = false, defaultValue = "0") final int page,
            @RequestParam(required = false, defaultValue = "50") final int perPage,
            @RequestParam(required = false, defaultValue = "createdAt") final List<String> orderBy,
            @RequestParam(required = false, defaultValue = "ASC")
                    final List<Sort.Direction> directions,
            @RequestParam(required = false, defaultValue = "") final Collection<String> includeJobs,
            @RequestParam(required = false, defaultValue = "") final Collection<String> excludeJobs,
            @RequestParam(required = false) final Instant from,
            @RequestParam(required = false) final Instant to,
            @RequestParam(required = false, defaultValue = "")
                    final Collection<JobRunQueueState> state) {
        return jobControllerService.getQueueInfo(
                toPageableWithDefaultDirection(page, perPage, orderBy, directions),
                state,
                includeJobs,
                excludeJobs,
                from,
                to);
    }

    @DeleteMapping("/queue/{jobQueuedRunId}")
    public SuccessResponse deleteQueuedRun(@PathVariable final int jobQueuedRunId) {
        return jobControllerService.deleteQueuedRun(jobQueuedRunId);
    }

    @PutMapping("/queue/{jobQueuedRunId}/priority")
    public SuccessResponse updateQueuedRunPriority(
            @PathVariable final int jobQueuedRunId,
            @RequestParam(required = false) final @PositiveOrZero Integer priority) {
        return jobControllerService.updateQueuedRunPriority(jobQueuedRunId, priority);
    }

    @PostMapping("/queue/{jobQueuedRunId}/allow-parallel-run")
    public SuccessResponse runQueuedallowParallelRun(@PathVariable final int jobQueuedRunId) {
        return jobControllerService.runQueuedallowParallelRun(jobQueuedRunId);
    }

    @DeleteMapping("/queue")
    public SuccessResponse deleteQueuedRuns(
            @RequestParam(required = false, defaultValue = "") final Collection<String> includeJobs,
            @RequestParam(required = false, defaultValue = "") final Collection<String> excludeJobs,
            @RequestParam(required = false, defaultValue = "")
                    final Collection<JobRunQueueState> state) {
        return jobControllerService.deleteQueuedRuns(includeJobs, excludeJobs, state);
    }

    @PostMapping("/queue/{jobQueuedRunId}/acknowledge")
    public SuccessResponse acknowledgeQueuedRun(@PathVariable final int jobQueuedRunId) {
        return jobControllerService.acknowledgeQueuedRun(jobQueuedRunId);
    }

    @PostMapping("/queue/acknowledge-cancelled")
    public SuccessResponse acknowledgeAllCancelledQueuedRuns() {
        return jobControllerService.acknowledgeAllCancelledQueuedRuns();
    }

    @GetMapping("/info")
    public JobSystemInfoResponse info() {
        return jobControllerService.getInfo();
    }

    @GetMapping("/instances")
    public JobInstanceInfosContainer instances() {
        return jobControllerService.getInstances();
    }

    @GetMapping("/run/{jobRunId}")
    public JobRunInfoContainer jobRun(@PathVariable final int jobRunId) {
        return jobControllerService.findJobRun(jobRunId);
    }

    @GetMapping("/job/{jobIdOrClassName}/properties")
    public Collection<JobPropertyInfo> getProperties(@PathVariable final String jobIdOrClassName) {
        return jobControllerService.getJobProperties(jobIdOrClassName);
    }

    @PostMapping("/job/{jobIdOrClassName}/properties")
    public Collection<JobPropertyInfo> updateProperties(
            @PathVariable final String jobIdOrClassName,
            @RequestBody final Map<String, String> updatedValues) {
        return jobControllerService.updateJobProperties(jobIdOrClassName, updatedValues);
    }

    @GetMapping("/job/{jobIdOrClassName}/triggers")
    public JobTriggers getTriggers(@PathVariable final String jobIdOrClassName) {
        return jobControllerService.getJobTriggers(jobIdOrClassName);
    }

    @PostMapping("/job/{jobIdOrClassName}/triggers")
    public JobTriggers updateTriggers(
            @PathVariable final String jobIdOrClassName,
            @RequestBody @Valid final JobTriggers updatedValues) {
        return jobControllerService.updateJobTriggers(jobIdOrClassName, updatedValues);
    }

    @PostMapping("/job/{jobIdOrClassName}/start")
    public ResponseEntity<QueueSubmissionResult> startJob(
            @PathVariable final String jobIdOrClassName,
            @RequestParam(required = false, defaultValue = "false") final boolean forceRun) {
        try {
            final var result = jobControllerService.startJobManual(jobIdOrClassName, forceRun);
            if (!result.queued()) {
                return ResponseEntity.badRequest()
                        .body(new QueueSubmissionFailure(result.notQueuedReason()));
            }
            return ResponseEntity.ok(new QueueSubmissionSuccess(result.run()));
        } catch (final ManualTriggerNotAllowedException e) {
            return ResponseEntity.badRequest().body(new QueueSubmissionFailure(e.toString()));
        }
    }

    @PostMapping("/job/{jobIdOrClassName}/enable")
    public ResponseEntity<StringResponse> enable(@PathVariable final String jobIdOrClassName) {
        jobControllerService.setEnabled(jobIdOrClassName, true);
        return ResponseEntity.ok(new StringResponse("Job enabled"));
    }

    @PostMapping("/job/{jobIdOrClassName}/disable")
    public ResponseEntity<StringResponse> disable(@PathVariable final String jobIdOrClassName) {
        jobControllerService.setEnabled(jobIdOrClassName, false);
        return ResponseEntity.ok(new StringResponse("Job disabled"));
    }

    @PostMapping("/job/start_all")
    public void startAllJobs(
            @RequestParam(required = false, defaultValue = "false") final boolean forceRun) {
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
                .map(b ->
                        ResponseEntity.ok()
                                .header(
                                        HttpHeaders.CONTENT_DISPOSITION,
                                        String.format(
                                                "attachment; filename=files_%d.zip", jobRunId))
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
            @PathVariable final String jobIdOrClassName, @PathVariable final int jobRunId) {
        try {
            final var result = jobControllerService.replayJobRun(jobIdOrClassName, jobRunId);
            if (!result.queued()) {
                return ResponseEntity.badRequest()
                        .body(new StringResponse(result.notQueuedReason()));
            }
            return ResponseEntity.ok(new StringResponse("Job successfully replayed"));
        } catch (final ManualTriggerNotAllowedException e) {
            return ResponseEntity.badRequest()
                    .body(
                            new StringResponse(
                                    "Job %s cannot be replayed.".formatted(jobIdOrClassName)));
        }
    }

    @PostMapping("/job/{jobIdOrClassName}/replay_run")
    public ResponseEntity<StringResponse> replayRun(@PathVariable final String jobIdOrClassName) {
        try {
            final var result = jobControllerService.replayJobRun(jobIdOrClassName);
            if (!result.queued()) {
                return ResponseEntity.badRequest()
                        .body(new StringResponse(result.notQueuedReason()));
            }
            return ResponseEntity.ok(new StringResponse("Job successfully replayed"));
        } catch (final ManualTriggerNotAllowedException e) {
            return ResponseEntity.badRequest()
                    .body(
                            new StringResponse(
                                    "Job %s cannot be replayed".formatted(jobIdOrClassName)));
        }
    }
}
