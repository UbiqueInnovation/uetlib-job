package ch.ubique.backend.job.controller;

import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.client.JobInfosContainer;
import ch.ubique.backend.job.model.client.JobInfosContainer.JobInfo;
import ch.ubique.backend.job.model.client.JobRunInfoContainer;
import ch.ubique.backend.job.model.client.JobRunInfosContainer;
import ch.ubique.backend.job.service.JobService;

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
@CrossOrigin({"https://status.systems.ubique.ch", "http://localhost:3000"})
public class BackendLibJobStatusController {

    private final JobService jobService;

    public BackendLibJobStatusController(final JobService jobService) {
        this.jobService = jobService;
    }

    @GetMapping("")
    public ResponseEntity<Void> up() {
        return ResponseEntity.noContent().build();
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
    public JobInfo updateJob(final @PathVariable int id, final @RequestParam String cronExpression)
            throws ClassNotFoundException, InvocationTargetException, NoSuchMethodException,
                    InstantiationException, IllegalAccessException {
        jobService.updateCronExpression(id, cronExpression);
        return jobService.findJobById(id);
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
