package ch.ubique.uetlib.job.service.inputs;

import ch.ubique.uetlib.job.model.input.JobRunInputDeclaration;
import java.io.IOException;
import java.io.InputStream;

public interface JobRunInputProvider {

    InputStream getJobRunInput(JobRunInputDeclaration inputDeclaration) throws IOException;
}
