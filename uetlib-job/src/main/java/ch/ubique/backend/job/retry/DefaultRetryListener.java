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
            final RetryContext retryContext,
            final RetryCallback<T, E> callback,
            final Throwable throwable) {
        logger.warn(
                "Got error while trying on retry #{}: ",
                retryContext.getRetryCount() - 1,
                throwable);
    }

    @Override
    public <T, E extends Throwable> boolean open(
            final RetryContext retryContext, final RetryCallback<T, E> callback) {
        logger.debug("Starting to run {} with retries enabled now.", callback);
        return true;
    }

    @Override
    public <T, E extends Throwable> void close(
            final RetryContext retryContext,
            final RetryCallback<T, E> callback,
            final Throwable throwable) {
        logger.debug("Done with retrying for {}.", callback);
    }

    @Override
    public <T, E extends Throwable> void onSuccess(
            final RetryContext retryContext, final RetryCallback<T, E> callback, final T result) {
        if (retryContext.getRetryCount() > 0) {
            logger.info("Successful after {} retries.", retryContext.getRetryCount());
        }
    }
}
