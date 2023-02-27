package ch.ubique.backend.job.controller;

import ch.ubique.backend.job.model.JobPropertyKey;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.client.JobInfosContainer;
import ch.ubique.backend.job.model.client.JobInfosContainer.JobInfo;
import ch.ubique.backend.job.model.client.JobProperty;
import ch.ubique.backend.job.model.client.JobRunInfoContainer;
import ch.ubique.backend.job.model.client.JobRunInfosContainer;
import ch.ubique.backend.job.service.JobService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
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

    @GetMapping("")
    public ResponseEntity<Void> up() {
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/jobs")
    public ResponseEntity<JobInfosContainer> jobs()
            throws ClassNotFoundException, InvocationTargetException, NoSuchMethodException,
                    InstantiationException, IllegalAccessException {
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

    @GetMapping("/job/{id}/properties")
    public List<JobProperty> getProperties(final @PathVariable int id) {
        return jobService.getJobProperties(id);
    }

    @PostMapping("/job/{id}/properties")
    public List<JobProperty> updateProperties(
            final @PathVariable int id,
            final @RequestBody Map<JobPropertyKey, String> updatedValues) {
        return jobService.updateJobProperties(id, updatedValues);
    }

    @PostMapping("/job/{id}/start")
    public void startJob(
            final @PathVariable int id,
            final @RequestParam(required = false, defaultValue = "false") boolean forceRun)
            throws ClassNotFoundException, InvocationTargetException, NoSuchMethodException,
                    InstantiationException, IllegalAccessException {
        jobService.startJob(id, forceRun);
    }

    @PostMapping("/job/start_all")
    public void startAllJobs(
            final @RequestParam(required = false, defaultValue = "false") boolean forceRun)
            throws ClassNotFoundException, InvocationTargetException, NoSuchMethodException,
                    InstantiationException, IllegalAccessException {
        jobService.startAllJobs(forceRun);
    }
}
