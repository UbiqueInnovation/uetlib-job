package ch.ubique.backend.job.controller;

import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.client.*;
import ch.ubique.backend.job.model.client.JobInfosContainer.JobInfo;
import ch.ubique.backend.job.model.client.info.JobSystemInfoResponse;
import ch.ubique.backend.job.service.JobService;

import org.quartz.SchedulerException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.InvocationTargetException;
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

    private final JobService jobService;

    public BackendLibJobStatusController(final JobService jobService) {
        this.jobService = jobService;
    }

    @GetMapping
    public ResponseEntity<Void> up() {
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/jobs")
    public JobInfosContainer jobs()
            throws ClassNotFoundException,
                    InvocationTargetException,
                    NoSuchMethodException,
                    InstantiationException,
                    IllegalAccessException {
        return jobService.findJobs();
    }

    @GetMapping("/runs")
    public JobRunInfosContainer jobRuns(
            @RequestParam(value = "page", required = false, defaultValue = "0") final int page,
            @RequestParam(value = "jobId", required = false) final Integer jobId,
            @RequestParam(value = "state", required = false) final JobRunState state) {
        return jobService.findJobRuns(page, jobId, state);
    }

    @GetMapping("/info")
    public JobSystemInfoResponse info() throws SchedulerException {
        return jobService.getInfo();
    }

    @GetMapping("/run/{jobRunId}")
    public JobRunInfoContainer jobRun(@PathVariable final int jobRunId) {
        return jobService.findJobRun(jobRunId);
    }

    @PostMapping("/job/{id}/update")
    public JobInfo updateJob(@PathVariable final int id, @RequestParam final String cronExpression)
            throws ClassNotFoundException,
                    InvocationTargetException,
                    NoSuchMethodException,
                    InstantiationException,
                    IllegalAccessException {
        jobService.updateCronExpression(id, cronExpression);
        return jobService.findJobById(id);
    }

    @GetMapping("/job/{id}/properties")
    public Collection<JobPropertyInfo> getProperties(@PathVariable final int id) {
        return jobService.getJobProperties(id);
    }

    @PostMapping("/job/{id}/properties")
    public Collection<JobPropertyInfo> updateProperties(
            @PathVariable final int id, @RequestBody final Map<String, String> updatedValues) {
        return jobService.updateJobProperties(id, updatedValues);
    }

    @PostMapping("/job/{id}/start")
    public void startJob(
            @PathVariable final int id,
            @RequestParam(required = false, defaultValue = "false") final boolean forceRun)
            throws ClassNotFoundException,
                    InvocationTargetException,
                    NoSuchMethodException,
                    InstantiationException,
                    IllegalAccessException {
        jobService.startJob(id, forceRun);
    }

    @PostMapping("/job/{className}/start")
    public void startJob(
            @PathVariable final String className,
            @RequestParam(required = false, defaultValue = "false") final boolean forceRun)
            throws ClassNotFoundException,
                    InvocationTargetException,
                    NoSuchMethodException,
                    InstantiationException,
                    IllegalAccessException {
        jobService.startJob(className, forceRun);
    }

    @PostMapping("/job/start_all")
    public void startAllJobs(
            @RequestParam(required = false, defaultValue = "false") final boolean forceRun)
            throws ClassNotFoundException,
                    InvocationTargetException,
                    NoSuchMethodException,
                    InstantiationException,
                    IllegalAccessException {
        jobService.startAllJobs(forceRun);
    }
}
