package ch.uetlib.job.service.executor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.uetlib.job.service.postgres.PgChannelListenerRegistry;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.function.Consumer;

class PgQueueInsertionListenServiceTest {

    private final PgChannelListenerRegistry channelListenerRegistry =
            mock(PgChannelListenerRegistry.class);
    private final JobRunQueueService jobRunQueueService = mock(JobRunQueueService.class);

    private final PgQueueInsertionListenService service =
            new PgQueueInsertionListenService(channelListenerRegistry, jobRunQueueService);

    @Test
    void doesNotListenWhenDatasourceIsNotPostgres() {
        when(channelListenerRegistry.isPostgres()).thenReturn(false);

        service.init();

        verify(channelListenerRegistry, never()).listen(any(), any());
    }

    @Test
    void checksForRunnableNotifiedRowsOnPostgres() {
        when(channelListenerRegistry.isPostgres()).thenReturn(true);

        service.init();

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Consumer<java.util.Collection<String>>> callbackCaptor =
                ArgumentCaptor.forClass(Consumer.class);
        verify(channelListenerRegistry)
                .listen(eq(PgQueueInsertionListenService.CHANNEL), callbackCaptor.capture());

        callbackCaptor.getValue().accept(List.of("42|7"));

        verify(jobRunQueueService).checkForRunnable(List.of(Pair.of(42, 7)));
    }
}
