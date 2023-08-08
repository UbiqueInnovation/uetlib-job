package ch.ubique.backend.job.controller;

import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.client.*;
import ch.ubique.backend.job.model.client.JobInfosContainer.JobInfo;
import ch.ubique.backend.job.model.client.info.JobSystemInfoResponse;
import ch.ubique.backend.job.service.controller.JobControllerService;

import org.quartz.SchedulerException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Map;

@RestController
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
    public JobInfosContainer jobs() throws ClassNotFoundException {
        return jobControllerService.findJobs();
    }

    @GetMapping("/runs")
    public JobRunInfosContainer jobRuns(
            @RequestParam(value = "page", required = false, defaultValue = "0") final int page,
            @RequestParam(value = "jobId", required = false) final Integer jobId,
            @RequestParam(value = "state", required = false) final JobRunState state) {
        return jobControllerService.findJobRuns(page, jobId, state);
    }

    @GetMapping("/info")
    public JobSystemInfoResponse info() throws SchedulerException {
        return jobControllerService.getInfo();
    }

    @GetMapping("/run/{jobRunId}")
    public JobRunInfoContainer jobRun(@PathVariable final int jobRunId) {
        return jobControllerService.findJobRun(jobRunId);
    }

    @PostMapping("/job/{id}/update")
    public JobInfo updateJob(@PathVariable final int id, @RequestParam final String cronExpression)
            throws ClassNotFoundException {
        return jobControllerService.updateCronExpression(id, cronExpression);
    }

    @GetMapping("/job/{id}/properties")
    public Collection<JobPropertyInfo> getProperties(@PathVariable final int id) {
        return jobControllerService.getJobProperties(id);
    }

    @PostMapping("/job/{id}/properties")
    public Collection<JobPropertyInfo> updateProperties(
            @PathVariable final int id, @RequestBody final Map<String, String> updatedValues) {
        return jobControllerService.updateJobProperties(id, updatedValues);
    }

    @PostMapping("/job/{idOrClassName}/start")
    public void startJob(
            @PathVariable final String idOrClassName,
            @RequestParam(required = false, defaultValue = "false") final boolean forceRun)
            throws ClassNotFoundException {
        try {
            jobControllerService.startJob(Integer.parseInt(idOrClassName), forceRun);
        } catch (final NumberFormatException ignored) {
            jobControllerService.startJob(idOrClassName, forceRun);
        }
    }

    @PostMapping("/job/start_all")
    public void startAllJobs(
            @RequestParam(required = false, defaultValue = "false") final boolean forceRun)
            throws ClassNotFoundException {
        jobControllerService.startAllJobs(forceRun);
    }
}
