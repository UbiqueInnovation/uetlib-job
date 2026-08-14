package ch.uetlib.job.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ch.uetlib.job.exception.ManualTriggerNotAllowedException;
import ch.uetlib.job.model.JobRunArguments;
import ch.uetlib.job.model.queue.JobRunQueueState;
import ch.uetlib.job.model.queue.QueueSubmissionFailure;
import ch.uetlib.job.model.queue.QueueSubmissionSuccess;
import ch.uetlib.job.model.response.SuccessResponse;
import ch.uetlib.job.model.JobQueuedRun;
import ch.uetlib.job.service.controller.JobControllerService;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * Covers the branching logic in this otherwise-thin delegating controller - the try/catch around
 * manual-trigger/replay, and the {@code Optional}-to-{@code ResponseEntity} mapping for file
 * downloads - which is the only real logic here, plus a couple of plain-delegation endpoints.
 */
class UetlibJobStatusControllerTest {

    private final JobControllerService jobControllerService = mock(JobControllerService.class);
    private final UetlibJobStatusController controller =
            new UetlibJobStatusController(jobControllerService);

    @Test
    void upReturnsNoContent() {
        assertEquals(HttpStatus.NO_CONTENT, controller.up().getStatusCode());
    }

    @Test
    void jobsDelegatesToTheControllerService() {
        controller.jobs();

        verify(jobControllerService).getJobInfos();
    }

    @Test
    void cancelJobRunDelegatesToTheControllerService() {
        when(jobControllerService.cancelJobRun(5)).thenReturn(new SuccessResponse(true, "ok"));

        assertTrue(controller.cancelJobRun(5).success());
    }

    @Test
    void startJobReturnsOkWithTheQueuedRunWhenAllowed() throws ManualTriggerNotAllowedException {
        final var queued = queuedRun();
        when(jobControllerService.startJobManual("job", true)).thenReturn(queued);

        final var response = controller.startJob("job", true);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        final var body = response.getBody();
        final var b = assertInstanceOf(QueueSubmissionSuccess.class, body);
        assertEquals(queued, b.queuedRun());
    }

    @Test
    void startJobReturnsBadRequestWhenManualTriggerIsNotAllowed()
            throws ManualTriggerNotAllowedException {
        when(jobControllerService.startJobManual("job", false))
                .thenThrow(new ManualTriggerNotAllowedException("nope"));

        final var response = controller.startJob("job", false);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertInstanceOf(QueueSubmissionFailure.class, response.getBody());
    }

    @Test
    void enableSetsTheJobEnabledAndReturnsAConfirmation() {
        final var response = controller.enable("job");

        verify(jobControllerService).setEnabled("job", true);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void disableSetsTheJobDisabledAndReturnsAConfirmation() {
        final var response = controller.disable("job");

        verify(jobControllerService).setEnabled("job", false);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void getAllFilesReturnsOkWithAContentDispositionHeaderWhenFilesExist() {
        final StreamingResponseBody body = out -> {};
        when(jobControllerService.getAllFiles(5)).thenReturn(Optional.of(body));

        final var response = controller.getAllFiles(5);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(
                response.getHeaders().getFirst("Content-Disposition").contains("files_5.zip"));
    }

    @Test
    void getAllFilesReturnsNotFoundWhenThereAreNoFiles() {
        when(jobControllerService.getAllFiles(5)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, controller.getAllFiles(5).getStatusCode());
    }

    @Test
    void getFileReturnsOkWithTheResourceWhenFound() {
        final var resource = new InputStreamResource(new ByteArrayInputStream(new byte[0]));
        when(jobControllerService.getFile(5, "a/b")).thenReturn(Optional.of(resource));

        final var response = controller.getFile(5, "/a/b");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(resource, response.getBody());
    }

    @Test
    void getFileReturnsNotFoundWhenMissing() {
        when(jobControllerService.getFile(5, "a/b")).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, controller.getFile(5, "/a/b").getStatusCode());
    }

    @Test
    void replayRunWithAnExplicitJobRunIdReturnsOkWhenAllowed()
            throws ManualTriggerNotAllowedException {
        final var response = controller.replayRun("job", 5);

        verify(jobControllerService).replayJobRun("job", 5);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void replayRunWithAnExplicitJobRunIdReturnsBadRequestWhenDisallowed()
            throws ManualTriggerNotAllowedException {
        doThrow(new ManualTriggerNotAllowedException("nope"))
                .when(jobControllerService)
                .replayJobRun("job", 5);

        assertEquals(HttpStatus.BAD_REQUEST, controller.replayRun("job", 5).getStatusCode());
    }

    @Test
    void replayRunWithoutAJobRunIdReturnsOkWhenAllowed() throws ManualTriggerNotAllowedException {
        final var response = controller.replayRun("job");

        verify(jobControllerService).replayJobRun("job");
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void replayRunWithoutAJobRunIdReturnsBadRequestWhenDisallowed()
            throws ManualTriggerNotAllowedException {
        doThrow(new ManualTriggerNotAllowedException("nope"))
                .when(jobControllerService)
                .replayJobRun("job");

        assertEquals(HttpStatus.BAD_REQUEST, controller.replayRun("job").getStatusCode());
    }

    private static JobQueuedRun queuedRun() {
        final var now = Instant.now();
        return new JobQueuedRun(
                1, null, 1, "1.0.0", JobRunQueueState.QUEUED, null, null, null, now, now,
                Set.of(), new JobRunArguments());
    }
}
