package ch.ubique.backend.job.exception;

import jakarta.validation.constraints.NotNull;

import java.text.MessageFormat;
import java.util.function.Supplier;

public class JobSubmissionException extends Exception {

    public JobSubmissionException() {}

    public JobSubmissionException(final String message) {
        super(message);
    }

    public JobSubmissionException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public static JobSubmissionException withMessage(
            @NotNull final String pattern, final Object... arguments) {
        return new JobSubmissionException(MessageFormat.format(pattern, arguments));
    }

    public static Supplier<JobSubmissionException> supplierWithMessage(
            @NotNull final String pattern, final Object... arguments) {
        return () -> withMessage(pattern, arguments);
    }
}
