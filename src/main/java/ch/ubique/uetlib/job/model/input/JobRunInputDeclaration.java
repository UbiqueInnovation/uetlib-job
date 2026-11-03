package ch.ubique.uetlib.job.model.input;

import static java.util.Objects.requireNonNull;

import ch.ubique.uetlib.core.file.ByteFile;
import ch.ubique.uetlib.job.model.JobRun;
import ch.ubique.uetlib.job.model.JobRunInput;

import jakarta.validation.constraints.NotBlank;

public record JobRunInputDeclaration(int jobRunId, @NotBlank String path, @NotBlank String hash) {

    public JobRunInputDeclaration(final int jobRunId, final String path) {
        this(jobRunId, path, "[no-hash]");
    }

    public static JobRunInputDeclaration from(final JobRun jobRun, final ByteFile file) {
        return new JobRunInputDeclaration(
                jobRun.id(),
                requireNonNull(file.getFileName()),
                requireNonNull(file.getFileHash()));
    }

    public static JobRunInputDeclaration from(final JobRunInput input) {
        return new JobRunInputDeclaration(
                requireNonNull(input.getRunId()),
                requireNonNull(input.getName()),
                requireNonNull(input.getHash()));
    }
}
