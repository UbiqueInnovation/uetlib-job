import {JobRunInfosContainer, JobRunState} from "../components/runs/Runs";

const API_BASE_URL = process.env.REACT_APP_API_URL || `${window.location.protocol}//${window.location.host}`
const API_URL = `${API_BASE_URL}/v1/status`;

export const loadJobs = () => fetch(`${API_URL}/jobs`).then(response => response.json());

export const loadRuns = () => fetch(`${API_URL}/runs`).then(response => response.json());

export const loadRun = (jobRunId: number) => fetch(`${API_URL}/run/${jobRunId}`)
    .then(response => response.json());

export const loadRunsPage = (page: number, jobId?: number, state?: JobRunState) => {
    const params = new URLSearchParams({page: page.toString()});
    if (jobId) {
        params.append("jobId", jobId.toString());
    }
    if (state) {
        params.append("state", state);
    }
    return fetch(`${API_URL}/runs?` +
        new URLSearchParams(params))
        .then<JobRunInfosContainer>(response => response.json());
};

export const startJob = (id: number, forceRun: boolean = false) =>
    fetch(`${API_URL}/job/${id}/start?` + new URLSearchParams({forceRun: forceRun.toString()}), {method: "post"})
        .then(() => true)
        .catch(() => false);

export const updateJob = (id: number) =>
    fetch(`${API_URL}/job/${id}/update`, {method: "post"})
        .then(() => true)
        .catch(() => false);

export const loadApplicationName = () => fetch(`${API_URL}/name`)
    .then(response => response.text());
