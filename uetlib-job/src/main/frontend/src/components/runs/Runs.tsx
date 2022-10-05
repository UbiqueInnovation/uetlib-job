import {useLoaderData, useSearchParams} from "react-router-dom";
import NavBar from "../nav/NavBar";
import {Col, FloatingLabel, Form, Pagination, Row, Table} from "react-bootstrap";
import Container from "react-bootstrap/Container";
import React, {useCallback, useEffect, useState} from "react";
import moment from "moment/moment";
import {loadRunsPage} from "../../dataProvider/dataProvider";
import {map, orderBy, toPairs} from "lodash-es";
import ShowLogsButton from "../common/ShowLogsButton";

const Runs = () => {
    const [jobRunInfosContainer, setJobRunInfosContainer] = useState<JobRunInfosContainer>(useLoaderData() as JobRunInfosContainer);

    const [jobIdSorted, setJobIdSorted] = useState<number[]>([]);

    const [searchParams, setSearchParams] = useSearchParams();
    const [currentPage, setCurrentPage] = useState(1);
    const [jobId, setJobId] = useState<number | undefined>(undefined);
    const [state, setState] = useState<JobRunState | undefined>(undefined);

    const getPageParam = useCallback(() => {
        const param = searchParams.get("page");
        return param && parseInt(param) || 0;
    }, [searchParams]);

    const getJobIdParam = useCallback(() => {
        const param = searchParams.get("jobId");
        return param && parseInt(param) || undefined;
    }, [searchParams]);

    const getStateParam = useCallback(() => {
        const param = searchParams.get("state");
        return param && param as JobRunState || undefined;
    }, [searchParams]);

    const updateParams: () => [number, number | undefined, JobRunState | undefined] = useCallback(() => {
        const pageParam = getPageParam();
        setCurrentPage(pageParam);
        const jobIdParam = getJobIdParam();
        setJobId(jobIdParam);
        const stateParam = getStateParam();
        setState(stateParam);

        return [pageParam, jobIdParam, stateParam];
    }, [getPageParam, getJobIdParam, getStateParam]);

    useEffect(() => {
        updateParams();
    });

    useEffect(() => {
        const [pageParam, jobIdParam, stateParam] = updateParams();
        loadRunsPage(pageParam, jobIdParam, stateParam).then(data => setJobRunInfosContainer(data));
    }, [updateParams, searchParams]);

    useEffect(() => {
        setJobIdSorted(map(orderBy(toPairs(jobRunInfosContainer.jobNames), 1), pair => pair[0] as unknown as number));
    }, [jobRunInfosContainer.jobNames]);

    const paginate = (page: number) => {
        if (page === 0) {
            if (searchParams.has("page")) {
                searchParams.delete("page");
                setSearchParams(searchParams);
            }
        } else {
            searchParams.set("page", page.toString())
            setSearchParams(searchParams);
        }
    };

    const resetPageParamAndSetParams = () => {
        if (searchParams.has("page")) {
            searchParams.delete("page");
        }
        setSearchParams(searchParams);
    };

    const onJobFilter = (jobIdSelect: string | undefined) => {
        if (!jobIdSelect) {
            if (searchParams.has("jobId")) {
                searchParams.delete("jobId");
            }
        } else {
            searchParams.set("jobId", jobIdSelect)
        }
        resetPageParamAndSetParams();
    };

    const onStateFilter = (stateSelect: string | undefined) => {
        if (!stateSelect) {
            if (searchParams.has("state")) {
                searchParams.delete("state");
            }
        } else {
            searchParams.set("state", stateSelect)
        }
        resetPageParamAndSetParams();
    };

    return (
        <>
            <NavBar/>
            <Container className="mt-3">
                <Row className="g-3">
                    <Col sm={6} lg={4} xl={3}>
                        <FloatingLabel controlId="jobFilter" label="Filter by Job">
                            <Form.Select aria-label="label for job filter"
                                         onChange={(e) => onJobFilter(e.target.value)}
                                         defaultValue={jobId}>
                                <option value="">-</option>
                                {jobIdSorted.map(jobIdValue => (
                                    <option
                                        key={jobIdValue}
                                        value={jobIdValue}>
                                        {jobRunInfosContainer.jobNames[jobIdValue]}
                                    </option>
                                ))}
                            </Form.Select>
                        </FloatingLabel>
                    </Col>
                    <Col sm={6} lg={4} xl={3}>
                        <FloatingLabel controlId="stateFilter" label="Filter by State">
                            <Form.Select aria-label="label for state filter"
                                         onChange={(e) => onStateFilter(e.target.value)}
                                         defaultValue={state}>
                                <option value="">-</option>
                                {jobRunStates.map(jobRunState => (
                                    <option
                                        key={jobRunState}
                                        value={jobRunState}>
                                        {jobRunState}
                                    </option>
                                ))}
                            </Form.Select>
                        </FloatingLabel>
                    </Col>
                </Row>
            </Container>
            <Container fluid className="mb-3">
                <Row className="g-3 my-1">
                    <Col>
                        <Table responsive striped bordered size="sm" className="m-0">
                            <thead>
                            <tr>
                                <th>Run ID</th>
                                <th>Job Name</th>
                                <th>Started at</th>
                                <th>Finished at</th>
                                <th className="text-end">Duration</th>
                                <th className="text-center" colSpan={2}>Actions</th>
                            </tr>
                            </thead>
                            <tbody>
                            {jobRunInfosContainer.jobRuns.map(jobRunInfo => (
                                <tr key={jobRunInfo.id}>
                                    <td className="align-middle text-center">{jobRunInfo.id}</td>
                                    <td className="align-middle">{jobRunInfosContainer.jobNames[jobRunInfo.jobId]}</td>
                                    <td className="align-middle">{formatTimestamp(jobRunInfo.startedAt)}</td>
                                    <td className="align-middle">
                                        {jobRunInfo.finishedAt != null && formatTimestamp(jobRunInfo.startedAt)}
                                    </td>
                                    <td className="align-middle text-end">{jobRunInfo.duration}</td>
                                    <td className="align-middle text-center">
                                        <ShowLogsButton jobRunInfo={jobRunInfo}/>
                                    </td>
                                </tr>
                            ))}
                            </tbody>
                        </Table>
                    </Col>
                </Row>
                <Row className="g-3 my-1">
                    <Col>
                        <Pagination className="justify-content-center">
                            <Pagination.First disabled={jobRunInfosContainer.first} onClick={() => paginate(0)}/>
                            <Pagination.Prev disabled={jobRunInfosContainer.first}
                                             onClick={() => paginate(currentPage - 1)}/>
                            <Pagination.Item active>{currentPage + 1}</Pagination.Item>
                            <Pagination.Next disabled={jobRunInfosContainer.last}
                                             onClick={() => paginate(currentPage + 1)}/>
                            <Pagination.Last disabled={jobRunInfosContainer.last}
                                             onClick={() => paginate(jobRunInfosContainer.totalPages - 1)}/>
                        </Pagination>
                    </Col>
                </Row>
            </Container>
        </>
    );
}

export type JobRunInfo = {
    id: number,
    jobId: number,
    jobVersion: number,
    state: JobRunState,
    startedAt: number,
    finishedAt: number,
    duration: string
};

export type JobRunInfosContainer = {
    jobRuns: JobRunInfo[],
    jobNames: { [k in number]: string }
    first: boolean,
    last: boolean,
    totalPages: number
};

export type JobRunInfoContainer = {
    jobRun: JobRunInfo,
    jobName: string
};

const jobRunStates = ["BUSY", "DONE", "ERROR", "UNFINISHED", "NONE", "SKIPPED"] as const;
export type JobRunState = typeof jobRunStates[number];

export const jobRunStateToColor = (state: JobRunState) => {
    switch (state) {
        case
        "BUSY":
            return "secondary";
        case "DONE":
            return "success";
        case "ERROR":
            return "danger";
        case "UNFINISHED":
            return "warning";
        case "NONE":
            return "secondary";
        case "SKIPPED":
            return "info";
    }
}

export const formatTimestamp = (millis: number) => moment.utc(millis).local().format('dd. MMM. yyyy HH:mm');

export default Runs;
