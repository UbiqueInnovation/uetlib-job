package ch.ubique.backend.job.exception;

import org.jspecify.annotations.NonNull;

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
            @NonNull final String pattern, final Object... arguments) {
        return new JobSubmissionException(MessageFormat.format(pattern, arguments));
    }

    public static Supplier<JobSubmissionException> supplierWithMessage(
            @NonNull final String pattern, final Object... arguments) {
        return () -> withMessage(pattern, arguments);
    }
}
