package ch.ubique.backend.job.model.executer;

public sealed interface JobSubmissionResult
        permits SuccessfulSubmissionResult, UnsuccessfulSubmissionResult {

    JobSubmissionStatus status();
}
