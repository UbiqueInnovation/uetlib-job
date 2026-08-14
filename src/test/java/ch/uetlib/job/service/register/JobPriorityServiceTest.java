package ch.uetlib.job.service.register;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ch.uetlib.job.data.service.JobDataService;
import ch.uetlib.job.model.JobClassAndId;
import ch.uetlib.job.service.JobService;
import ch.uetlib.job.testjob.TestJob;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

/** Covers the private {@code onStartup} event listener - this class's entire behavior. */
class JobPriorityServiceTest {

    private static final JobClassAndId JOB = new JobClassAndId(1, TestJob.class);

    private final JobService jobService = mock(JobService.class);
    private final JobDataService jobDataService = mock(JobDataService.class);
    private final JobPriorityService service = new JobPriorityService(jobService, jobDataService);

    @Test
    void syncsPriorityWhenTheStoredValueDiffersFromTheJobsOwn() {
        when(jobDataService.getPriorities()).thenReturn(Map.of(JOB.id(), 1));
        when(jobService.getAllJobClasses()).thenReturn(List.of(TestJob.class));
        when(jobService.findJobIdOrThrow(TestJob.class)).thenReturn(JOB.id());
        when(jobService.instantiateJob(TestJob.class)).thenReturn(new TestJob());

        ReflectionTestUtils.invokeMethod(service, "onStartup");

        verify(jobDataService).updatePriority(JOB.id(), 0);
    }

    @Test
    void syncsPriorityWhenNoneIsStoredYet() {
        when(jobDataService.getPriorities()).thenReturn(Map.of());
        when(jobService.getAllJobClasses()).thenReturn(List.of(TestJob.class));
        when(jobService.findJobIdOrThrow(TestJob.class)).thenReturn(JOB.id());
        when(jobService.instantiateJob(TestJob.class)).thenReturn(new TestJob());

        ReflectionTestUtils.invokeMethod(service, "onStartup");

        verify(jobDataService).updatePriority(JOB.id(), 0);
    }

    @Test
    void doesNotWriteWhenTheStoredPriorityAlreadyMatches() {
        when(jobDataService.getPriorities()).thenReturn(Map.of(JOB.id(), 0));
        when(jobService.getAllJobClasses()).thenReturn(List.of(TestJob.class));
        when(jobService.findJobIdOrThrow(TestJob.class)).thenReturn(JOB.id());
        when(jobService.instantiateJob(TestJob.class)).thenReturn(new TestJob());

        ReflectionTestUtils.invokeMethod(service, "onStartup");

        verify(jobDataService, never()).updatePriority(anyInt(), anyInt());
    }
}
