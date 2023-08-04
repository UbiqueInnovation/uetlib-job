package ch.ubique.backend.job.runners;

import ch.ubique.backend.job.Job;

import io.kubernetes.client.openapi.ApiException;

import java.io.IOException;

public interface JobRunner {

    void run(Job job) throws IOException, ApiException;
}
