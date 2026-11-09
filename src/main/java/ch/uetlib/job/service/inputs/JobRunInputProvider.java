package ch.uetlib.job.service.inputs;

import ch.uetlib.job.model.input.JobRunInputDeclaration;

import java.io.IOException;
import java.io.InputStream;

public interface JobRunInputProvider {

    InputStream getJobRunInput(JobRunInputDeclaration inputDeclaration) throws IOException;
}
