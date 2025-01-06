package ch.ubique.backend.job.files;

import ch.ubique.backend.core.io.InputStreamProvider;
import ch.ubique.backend.core.utils.ArrayUtils;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.input.JobRunInputDeclaration;
import ch.ubique.backend.job.storage.JobRunStorage;
import ch.ubique.backend.storage.output.Output;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import org.springframework.stereotype.Service;

@Service
public class JobRunInputManager {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final JobRunStorage jobRunStorage;
    private final JobDataService jobDataService;

    public JobRunInputManager(
            final JobRunStorage jobRunStorage, final JobDataService jobDataService) {
        this.jobRunStorage = jobRunStorage;
        this.jobDataService = jobDataService;
    }

    public void save(final JobRunInputDeclaration inputDeclaration, final Output output)
            throws IOException {
        saveToStorage(inputDeclaration, output);
        saveToDatabase(inputDeclaration);
    }

    public void saveToDatabase(final Collection<JobRunInputDeclaration> inputDeclarations) {
        logger.info("Saving {} inputs to database.", inputDeclarations.size());
        jobDataService.insertJobRunInput(inputDeclarations);
    }

    public void saveToDatabase(final JobRunInputDeclaration inputDeclaration) {
        logger.info("Saving {} to database.", inputDeclaration);
        jobDataService.insertJobRunInput(inputDeclaration);
    }

    public void saveToStorage(final JobRunInputDeclaration inputDeclaration, final Output output)
            throws IOException {
        logger.info("Saving to {} storage.", inputDeclaration);
        jobRunStorage.saveJobRunInput(
                output.gZip(),
                getJobSimpleName(inputDeclaration),
                JobRun.readableId(inputDeclaration.jobRunId()),
                inputDeclaration.path());
    }

    public Collection<JobRunInputDeclaration> getDeclarations(final int jobRunId) {
        return jobDataService.findJobRunInputs(jobRunId).stream()
                .map(JobRunInputDeclaration::from)
                .toList();
    }

    public Collection<JobFile> getAllJobRunFiles(final int jobRunId) {
        return getDeclarations(jobRunId).stream().map(this::createJobFile).toList();
    }

    public JobFile createJobFile(final JobRunInputDeclaration inputDeclaration) {
        return new JobFile(
                inputDeclaration.path(),
                InputStreamProvider.of(() -> createInputStream(inputDeclaration)));
    }

    public InputStream createInputStream(final JobRunInputDeclaration inputDeclaration)
            throws IOException {
        final var jobSimpleName = getJobSimpleName(inputDeclaration);
        return jobRunStorage.loadJobRunInput(
                jobSimpleName,
                JobRun.readableId(inputDeclaration.jobRunId()),
                inputDeclaration.path());
    }

    private String getJobSimpleName(final JobRunInputDeclaration inputDeclaration) {
        final var jobId = jobDataService.findJobRunById(inputDeclaration.jobRunId()).orElseThrow();
        final var job = jobDataService.findJobById(jobId.id()).orElseThrow();
        return ArrayUtils.lastElement(job.canonicalClassName().split("\\."));
    }
}
