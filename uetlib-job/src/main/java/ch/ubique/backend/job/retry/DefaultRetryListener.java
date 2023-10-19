package ch.ubique.backend.job.retry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.stereotype.Component;

@Component
public class DefaultRetryListener implements RetryListener {

    private static final Logger logger = LoggerFactory.getLogger(DefaultRetryListener.class);

    @Override
    public <T, E extends Throwable> void onError(
            final RetryContext context,
            final RetryCallback<T, E> callback,
            final Throwable throwable) {
        logger.warn("Got error while trying on retry #{}: ", context.getRetryCount(), throwable);
    }

    @Override
    public <T, E extends Throwable> boolean open(
            final RetryContext context, final RetryCallback<T, E> callback) {
        if (context.getLastThrowable() != null) {
            logger.info("First time retrying after throwing:", context.getLastThrowable());
        }
        return true;
    }

    @Override
    public <T, E extends Throwable> void close(
            final RetryContext context,
            final RetryCallback<T, E> callback,
            final Throwable throwable) {
        if (context.getRetryCount() > 0 || context.getLastThrowable() != null) {
            logger.info(
                    "Last time retrying after throwing after retries {}:",
                    context.getRetryCount(),
                    context.getLastThrowable());
        }
    }

    @Override
    public <T, E extends Throwable> void onSuccess(
            final RetryContext context, final RetryCallback<T, E> callback, final T result) {
        if (context.getRetryCount() > 0) {
            logger.info("Successful on retry #{}:", context.getRetryCount());
        }
    }
}
