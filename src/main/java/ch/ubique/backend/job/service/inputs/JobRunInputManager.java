package ch.ubique.backend.job.service.inputs;

import ch.ubique.backend.core.utils.ArrayUtils;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.input.JobRunInputDeclaration;
import ch.ubique.backend.job.service.JobService;
import ch.ubique.backend.job.storage.JobRunStorage;
import ch.ubique.backend.storage.output.Output;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;

@Service
public class JobRunInputManager implements JobRunInputProvider {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final JobRunStorage jobRunStorage;

    private final JobService jobService;
    private final JobDataService jobDataService;

    public JobRunInputManager(
            final JobRunStorage jobRunStorage,
            final JobService jobService,
            final JobDataService jobDataService) {
        this.jobRunStorage = jobRunStorage;
        this.jobService = jobService;
        this.jobDataService = jobDataService;
    }

    public void save(final JobRunInputDeclaration inputDeclaration, final Output output)
            throws IOException {
        saveToStorage(inputDeclaration, output);
        saveToDatabase(inputDeclaration);
    }

    public void save(final Map<JobRunInputDeclaration, Output> inputs) throws IOException {
        for (final var entry : inputs.entrySet()) {
            saveToStorage(entry.getKey(), entry.getValue());
        }
        saveToDatabase(inputs.keySet());
    }

    public void saveToDatabase(final Collection<JobRunInputDeclaration> inputDeclarations) {
        if (inputDeclarations.isEmpty()) {
            return;
        }
        logger.debug("Saving {} inputs to database.", inputDeclarations.size());
        jobDataService.insertJobRunInput(inputDeclarations);
    }

    public void saveToDatabase(final JobRunInputDeclaration inputDeclaration) {
        logger.debug("Saving {} to database.", inputDeclaration);
        jobDataService.insertJobRunInput(inputDeclaration);
    }

    public void saveToStorage(final JobRunInputDeclaration inputDeclaration, final Output output)
            throws IOException {
        logger.debug("Saving {} to storage.", inputDeclaration);
        jobRunStorage.saveJobRunInput(
                output,
                getJobSimpleName(inputDeclaration),
                JobRun.readableId(inputDeclaration.jobRunId()),
                inputDeclaration.path());
    }

    public Collection<String> getDeclaredInputPaths(final int jobRunId) {
        return getInputDeclarations(jobRunId).stream().map(JobRunInputDeclaration::path).toList();
    }

    public Collection<JobRunInputDeclaration> getInputDeclarations(final int jobRunId) {
        return jobDataService.findJobRunInputs(jobRunId).stream()
                .map(JobRunInputDeclaration::from)
                .toList();
    }

    public boolean hasDeclaredInputs(final int jobRunId) {
        return jobDataService.hasJobRunInputs(jobRunId);
    }

    @Override
    public InputStream getJobRunInput(final JobRunInputDeclaration inputDeclaration)
            throws IOException {
        final var jobSimpleName = getJobSimpleName(inputDeclaration);
        return jobRunStorage.loadJobRunInput(
                jobSimpleName,
                JobRun.readableId(inputDeclaration.jobRunId()),
                inputDeclaration.path());
    }

    public boolean exists(final JobRunInputDeclaration inputDeclaration) throws IOException {
        final var jobSimpleName = getJobSimpleName(inputDeclaration);
        return jobRunStorage.jobRunInputExists(
                jobSimpleName,
                JobRun.readableId(inputDeclaration.jobRunId()),
                inputDeclaration.path());
    }

    private String getJobSimpleName(final JobRunInputDeclaration inputDeclaration) {
        final var jobRun = jobDataService.findJobRunById(inputDeclaration.jobRunId()).orElseThrow();
        final var job = jobService.findJobByIdOrThrow(jobRun.jobId());
        return ArrayUtils.lastElement(job.canonicalClassName().split("\\."));
    }
}
