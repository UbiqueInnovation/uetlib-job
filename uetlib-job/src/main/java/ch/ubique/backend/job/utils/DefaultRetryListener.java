package ch.ubique.backend.job.utils;

import org.slf4j.Logger;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;

public class DefaultRetryListener implements RetryListener {

    private final Logger logger;

    public DefaultRetryListener(final Logger logger) {
        this.logger = logger;
    }

    @Override
    public <T, E extends Throwable> void onError(
            final RetryContext context,
            final RetryCallback<T, E> callback,
            final Throwable throwable) {
        logger.warn("Got error while trying: ", throwable);
    }

    @Override
    public <T, E extends Throwable> boolean open(
            final RetryContext context, final RetryCallback<T, E> callback) {
        // Version 2.0.0 of spring-retry does not have a default implementation, which causes an
        // error in client projects, where spring-retry < 2.0.1 is used.
        // Please do not remove this method.
        return true;
    }

    @Override
    public <T, E extends Throwable> void close(
            final RetryContext context,
            final RetryCallback<T, E> callback,
            final Throwable throwable) {
        // Version 2.0.0 of spring-retry does not have a default implementation, which causes an
        // error in client projects, where spring-retry < 2.0.1 is used.
        // Please do not remove this method.
    }

    @Override
    public <T, E extends Throwable> void onSuccess(
            final RetryContext context, final RetryCallback<T, E> callback, final T result) {
        // Version 2.0.0 of spring-retry does not have a default implementation, which causes an
        // error in client projects, where spring-retry < 2.0.1 is used.
        // Please do not remove this method.
    }
}
