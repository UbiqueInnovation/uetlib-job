package ch.ubique.backend.job.model.input;

import ch.ubique.backend.core.file.ByteFile;
import ch.ubique.backend.job.model.JobRun;

import ch.ubique.backend.job.model.JobRunInput;
import jakarta.validation.constraints.NotBlank;

public record JobRunInputDeclaration(int jobRunId, @NotBlank String path, @NotBlank String hash) {

    public JobRunInputDeclaration(final String path, final int jobRunId) {
        this(jobRunId, path, "[no-hash]");
    }

    public static JobRunInputDeclaration from(final JobRun jobRun, final ByteFile file) {
        return new JobRunInputDeclaration(jobRun.id(), file.getFileName(), file.getFileHash());
    }

    public static JobRunInputDeclaration from(final JobRunInput input) {
        return new JobRunInputDeclaration(input.getRunId(), input.getName(), input.getHash());
    }
}
