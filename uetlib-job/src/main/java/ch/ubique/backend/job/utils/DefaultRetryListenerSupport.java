package ch.ubique.backend.job.utils;

import org.slf4j.Logger;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.listener.RetryListenerSupport;

public class DefaultRetryListenerSupport extends RetryListenerSupport {

    private final Logger logger;

    public DefaultRetryListenerSupport(final Logger logger) {
        this.logger = logger;
    }

    @Override
    public <T, E extends Throwable> void onError(
            final RetryContext context,
            final RetryCallback<T, E> callback,
            final Throwable throwable) {
        logger.warn("Got error while trying.", throwable);
        super.onError(context, callback, throwable);
    }
}
