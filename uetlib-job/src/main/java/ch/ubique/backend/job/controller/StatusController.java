package ch.ubique.backend.job.controller;

import static ch.ubique.backend.core.utils.StringFormattingUtils.millisToString;

import ch.ubique.backend.core.exception.ResourceNotFoundException;
import ch.ubique.backend.job.BaseInputJob;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.Job;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.scheduler.JobScheduler;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/status")
public class StatusController {

    private final JobDataService jobDataService;
    private final JobScheduler jobScheduler;

    private static final int PAGE_SIZE = 30;

    public StatusController(final JobDataService jobDataService, final JobScheduler jobScheduler) {
        this.jobDataService = jobDataService;
        this.jobScheduler = jobScheduler;
    }

    @GetMapping("")
    public ModelAndView status() {
        return redirectToStatusList();
    }

    private ModelAndView redirectToStatusList() {
        return new ModelAndView("redirect:/status/list");
    }

    @GetMapping("list")
    public ModelAndView jobList() {
        final var mav = new ModelAndView("list");
        final var jobs = jobDataService.findJobs();
        jobs.sort(Comparator.comparingInt(Job::getId));
        final var latestJobRuns =
                jobs.stream()
                        .collect(
                                HashMap::new,
                                (map, job) ->
                                        map.put(
                                                job.getId(),
                                                jobDataService
                                                        .findLatestJobRun(job.getId())
                                                        .orElse(null)),
                                HashMap::putAll);
        final var forceRunnableJobs =
                jobs.stream()
                        .collect(
                                Collectors.toMap(
                                        Job::getId,
                                        job -> {
                                            try {
                                                return BaseInputJob.class.isAssignableFrom(
                                                        Class.forName(job.getClazz()));
                                            } catch (ClassNotFoundException e) {
                                                return false;
                                            }
                                        }));

        mav.addObject("jobs", jobs);
        mav.addObject("latestJobRuns", latestJobRuns);
        mav.addObject("jobRunStateColors", jobRunStateColors);
        mav.addObject("forceRunnableJobs", forceRunnableJobs);
        return mav;
    }

    @GetMapping("runs")
    public ModelAndView jobRuns(
            @RequestParam(value = "currentPage", required = false, defaultValue = "0")
                    int currentPage,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "state", required = false) JobRunState state) {
        final var mav = new ModelAndView("runs");

        final var jobs = jobDataService.findJobs();
        jobs.sort(Comparator.comparing(Job::getName));
        final var jobRunsPage = jobDataService.findJobRuns(currentPage, PAGE_SIZE, name, state);
        final var jobRuns = jobRunsPage.getContent();
        final var jobRunDurations =
                jobRuns.stream()
                        .collect(
                                Collectors.toUnmodifiableMap(
                                        JobRun::getId,
                                        jobRun -> {
                                            if (jobRun.getFinishedAt() != null) {
                                                return millisToString(
                                                        jobRun.getFinishedAt().toEpochMilli()
                                                                - jobRun.getStartedAt()
                                                                        .toEpochMilli());
                                            } else {
                                                return "-";
                                            }
                                        }));

        final var jobIdToName =
                jobs.stream().collect(Collectors.toUnmodifiableMap(Job::getId, Job::getName));
        final var jobNameSelected =
                jobIdToName.values().stream()
                        .collect(
                                Collectors.toUnmodifiableMap(
                                        Function.identity(), jobName -> jobName.equals(name)));

        final var states = Arrays.stream(JobRunState.values()).toList();
        final var stateSelected =
                states.stream()
                        .collect(
                                Collectors.toUnmodifiableMap(
                                        Function.identity(),
                                        jobRunState -> jobRunState.equals(state)));

        mav.addObject("jobs", jobs);
        mav.addObject("jobRuns", jobRuns);
        mav.addObject("jobIdToName", jobIdToName);
        mav.addObject("jobNameSelected", jobNameSelected);
        mav.addObject("states", states);
        mav.addObject("stateSelected", stateSelected);
        mav.addObject("jobRunDurations", jobRunDurations);
        mav.addObject("jobRunStateColors", jobRunStateColors);
        mav.addObject("currentPage", currentPage);
        mav.addObject("isFirst", jobRunsPage.isFirst());
        mav.addObject("isLast", jobRunsPage.isLast());
        return mav;
    }

    @PostMapping("job/{clazz}/update")
    public ModelAndView updateJob(
            final @PathVariable String clazz, final @RequestParam String cronExpression)
            throws ClassNotFoundException, InvocationTargetException, NoSuchMethodException,
                    InstantiationException, IllegalAccessException {
        final var job = jobDataService.updateCronExpression(clazz, cronExpression);
        jobScheduler.update(job);
        return redirectToStatusList();
    }

    @GetMapping("job/{clazz}/start")
    public RedirectView startJob(
            @PathVariable String clazz,
            @RequestParam(required = false, defaultValue = "false") boolean forceRun,
            RedirectAttributes attributes)
            throws ClassNotFoundException, InvocationTargetException, NoSuchMethodException,
                    InstantiationException, IllegalAccessException {
        final var job =
                jobDataService
                        .findJobByClass(clazz)
                        .orElseThrow(
                                ResourceNotFoundException.throwWithMessage(
                                        "No Job with clazz {0} found", clazz));
        jobScheduler.runNow(job, forceRun);

        attributes.addFlashAttribute("triggeredJob", job);
        return new RedirectView("/status/list");
    }

    @GetMapping("runs/{jobRunId}/log")
    public HttpEntity<byte[]> log(@PathVariable Integer jobRunId) throws IOException {
        Optional<String> importLog = jobDataService.findLogForJobRun(jobRunId);
        if (importLog.isPresent()) {
            byte[] fileBytes = getLogOutput(importLog.get());
            HttpHeaders header = new HttpHeaders();
            header.setContentType(MediaType.TEXT_PLAIN);
            header.setContentLength(fileBytes.length);
            return new HttpEntity<>(fileBytes, header);
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    private final Map<JobRunState, String> jobRunStateColors =
            Map.of(
                    JobRunState.BUSY, "secondary",
                    JobRunState.DONE, "success",
                    JobRunState.ERROR, "danger",
                    JobRunState.UNFINISHED, "warning",
                    JobRunState.NONE, "secondary",
                    JobRunState.SKIPPED, "info");

    private byte[] getLogOutput(String content) throws IOException {
        if (StringUtils.isBlank(content)) {
            return "no content".getBytes();
        }
        final String filePrefix = "file:";
        if (content.startsWith(filePrefix)) {
            return FileUtils.readFileToByteArray(new File(content.substring(filePrefix.length())));
        }
        return content.getBytes();
    }
}
