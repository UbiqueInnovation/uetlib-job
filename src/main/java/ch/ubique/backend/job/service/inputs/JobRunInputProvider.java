package ch.ubique.backend.job.service.inputs;

import ch.ubique.backend.job.model.input.JobRunInputDeclaration;

import java.io.IOException;
import java.io.InputStream;

public interface JobRunInputProvider {

    InputStream getJobRunInput(JobRunInputDeclaration inputDeclaration) throws IOException;
}
