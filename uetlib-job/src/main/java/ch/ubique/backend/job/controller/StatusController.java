package ch.ubique.backend.job.controller;

import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.client.JobInfosContainer;
import ch.ubique.backend.job.model.client.JobRunInfoContainer;
import ch.ubique.backend.job.model.client.JobRunInfosContainer;
import ch.ubique.backend.job.service.JobService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.InvocationTargetException;

@RestController
@RequestMapping("/v1/status")
@CrossOrigin(value = {"${job.interface.baseUrl}", "http://localhost:3000"})
public class StatusController {

    private final JobService jobService;
    private final String applicationName;

    public StatusController(
            final JobService jobService,
            final @Value("${spring.application.name}") String applicationName) {
        this.jobService = jobService;
        this.applicationName = applicationName;
    }

    @GetMapping("/name")
    public ResponseEntity<String> applicationName() {
        return ResponseEntity.ok(applicationName);
    }

    @GetMapping("/jobs")
    public ResponseEntity<JobInfosContainer> jobs() {
        return ResponseEntity.ok(jobService.findJobs());
    }

    @GetMapping("/runs")
    public ResponseEntity<JobRunInfosContainer> jobRuns(
            final @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            final @RequestParam(value = "jobId", required = false) Integer jobId,
            final @RequestParam(value = "state", required = false) JobRunState state) {
        return ResponseEntity.ok(jobService.findJobRuns(page, jobId, state));
    }

    @GetMapping("/run/{jobRunId}")
    public ResponseEntity<JobRunInfoContainer> jobRun(final @PathVariable int jobRunId) {
        return ResponseEntity.ok(jobService.findJobRun(jobRunId));
    }

    @PostMapping("/job/{id}/update")
    public void updateJob(final @PathVariable int id, final @RequestParam String cronExpression)
            throws ClassNotFoundException, InvocationTargetException, NoSuchMethodException,
                    InstantiationException, IllegalAccessException {
        jobService.updateCronExpression(id, cronExpression);
    }

    @PostMapping("/job/{id}/start")
    public void startJob(
            final @PathVariable int id,
            final @RequestParam(required = false, defaultValue = "false") boolean forceRun)
            throws ClassNotFoundException, InvocationTargetException, NoSuchMethodException,
                    InstantiationException, IllegalAccessException {
        jobService.startJob(id, forceRun);
    }
}
