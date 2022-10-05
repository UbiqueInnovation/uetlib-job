import {useLoaderData} from "react-router-dom";
import React, {useEffect, useState} from "react";
import {formatTimestamp, JobRunInfo} from "../runs/Runs";
import NavBar from "../nav/NavBar";
import Container from "react-bootstrap/Container";
import {Badge, Button, CloseButton, Col, Form, InputGroup, Modal, Row, Table, Toast} from "react-bootstrap";
import {startJob, updateJob} from "../../dataProvider/dataProvider";
import {findIndex} from "lodash-es";
import ShowLogsButton from "../common/ShowLogsButton";

const List = () => {
    const [jobInfos, setJobInfos] = useState<JobInfo[]>([]);
    const [latestRuns, setLatestRuns] = useState<{ [k in number]: JobRunInfo }>({});

    const [showTriggeredToast, setShowTriggeredToast] = useState(false);
    const [showModal, setShowModal] = useState(false);
    const [triggeredJob, setTriggeredJob] = useState<JobInfo | undefined>(undefined);

    const [showUpdatedToast, setShowUpdatedToast] = useState(false);
    const [updatedJob, setUpdatedJob] = useState<JobInfo | undefined>(undefined);

    const jobInfosContainer = useLoaderData() as JobInfosContainer;

    useEffect(() => {
        setJobInfos(jobInfosContainer.jobs);
        setLatestRuns(jobInfosContainer.latestRuns);
    }, [jobInfosContainer]);

    const handleStartJob = (jobInfo: JobInfo, e: React.FormEvent<HTMLFormElement>) => {
        e.preventDefault();
        startJob(jobInfo.id).then(started => {
            if (started) {
                setTriggeredJob(jobInfo);
                setShowTriggeredToast(true);
            }
        });
    };

    const handleShowModal = (jobInfo: JobInfo) => {
        setTriggeredJob(jobInfo);
        setShowModal(true);
    };

    const handleStartJobModal = (forceRun?: boolean) => {
        triggeredJob && startJob(triggeredJob.id, forceRun).then(started => {
            if (started) {
                setShowModal(false);
                setShowTriggeredToast(true);
            }
        });
    };

    const handleUpdateJob = (jobInfo: JobInfo, e: React.FormEvent<HTMLFormElement>) => {
        e.preventDefault();
        updateJob(jobInfo.id, e.currentTarget.cronExpression.value).then(updatedJobInfo => {
            jobInfos[findIndex(jobInfos, ji => ji.id === jobInfo.id)] = updatedJobInfo;
            setJobInfos(jobInfos);
            setUpdatedJob(updatedJobInfo);
            setShowUpdatedToast(true);
        });
    };

    return (
        <>
            <NavBar/>
            <Container fluid className="my-3">
                <Row className="g-3">
                    <Col>
                        <Table responsive striped bordered size="sm" className="m-0">
                            <thead>
                            <tr>
                                <th className="text-center">ID</th>
                                <th>Name</th>
                                <th>Cron</th>
                                <th>Started At</th>
                                <th>Finished At</th>
                                <th className="text-center" colSpan={2}>Actions</th>
                            </tr>
                            </thead>
                            <tbody>
                            {jobInfos.map(jobInfo => (
                                <tr key={jobInfo.id}>
                                    <td className="align-middle text-center">{jobInfo.id}</td>
                                    <td className="align-middle">{jobInfo.name}</td>
                                    <td>
                                        <Form onSubmit={(e) => handleUpdateJob(jobInfo, e)}>
                                            <InputGroup size="sm" hasValidation>
                                                {(jobInfo.cronActive || !jobInfo.cronExpressionValid) &&
                                                    <Badge bg={jobInfo.cronActive ? "success" : "danger"}
                                                           className="d-inline-flex align-items-center justify-content-center">
                                                        {jobInfo.cronActive ? "Active" : "Invalid"}
                                                    </Badge>
                                                }
                                                <Form.Control placeholder="Cron expression" aria-label="Cron expression"
                                                              aria-describedby="button-submit"
                                                              isValid={jobInfo.cronExpressionValid}
                                                              isInvalid={!jobInfo.cronExpressionValid}
                                                              name="cronExpression"
                                                              defaultValue={jobInfo.cronExpression}/>
                                                <Button type="submit" variant="outline-secondary" id="button-submit">
                                                    Save
                                                </Button>
                                            </InputGroup>
                                        </Form>
                                    </td>
                                    <td className="align-middle">{latestRuns[jobInfo.id] &&
                                        formatTimestamp(latestRuns[jobInfo.id].startedAt)}</td>
                                    <td className="align-middle">{latestRuns[jobInfo.id]?.finishedAt != null &&
                                        formatTimestamp(latestRuns[jobInfo.id].finishedAt)}</td>
                                    <td className="align-middle text-center">
                                        {latestRuns[jobInfo.id] &&
                                            <ShowLogsButton jobRunInfo={latestRuns[jobInfo.id]}/>
                                        }
                                    </td>
                                    <td className="align-middle text-center">
                                        {/* Job is NOT force runnable */}
                                        {!jobInfo.forceRunnable &&
                                            <Form onSubmit={(e) => handleStartJob(jobInfo, e)}>
                                                <Button type="submit" variant="success" size="sm">
                                                    <i className="bi bi-play-fill"></i> Start now
                                                </Button>
                                            </Form>
                                        }

                                        {/* Job is force runnable */}
                                        {jobInfo.forceRunnable && (
                                            <div>
                                                {/* Button trigger modal */}
                                                <Form onSubmit={() => handleShowModal(jobInfo)}>
                                                    <Button type="submit" variant="success" size="sm">
                                                        <i className="bi bi-play-fill"></i> Start now
                                                    </Button>
                                                </Form>

                                                {/* Modal */}
                                                <Modal show={showModal} onHide={() => setShowModal(false)}>
                                                    <Modal.Header closeButton>
                                                        <Modal.Title>Force run?</Modal.Title>
                                                    </Modal.Header>
                                                    <Modal.Body>
                                                        Do you want to force
                                                        run <strong><span>{triggeredJob?.name}</span></strong>?
                                                    </Modal.Body>
                                                    <Modal.Footer>
                                                        <Button variant="danger"
                                                                onClick={() => handleStartJobModal(true)}>
                                                            Close
                                                        </Button>
                                                        <Button variant="primary" autoFocus
                                                                onClick={() => handleStartJobModal(false)}>
                                                            Skip if unchanged
                                                        </Button>
                                                    </Modal.Footer>
                                                </Modal>
                                            </div>
                                        )}
                                    </td>
                                </tr>
                            ))}
                            </tbody>
                        </Table>
                    </Col>
                </Row>
            </Container>

            <Toast show={showTriggeredToast} onClose={() => setShowTriggeredToast(false)} autohide delay={5000}
                   className="position-fixed top-0 end-0 mt-5 me-3 align-items-center text-bg-success border-0"
                   style={{zIndex: "1020"}} role="alert" aria-live="assertive" aria-atomic="true">
                <Toast.Body>
                    Job <strong><span>{triggeredJob?.name}</span></strong> triggered.
                    <CloseButton type="submit" variant="white" className="me-2 m-auto position-absolute end-0"
                                 aria-label="Close" onClick={() => setShowTriggeredToast(false)}/>
                </Toast.Body>
            </Toast>

            <Toast show={showUpdatedToast} onClose={() => setShowUpdatedToast(false)} autohide delay={5000}
                   className="position-fixed top-0 end-0 mt-5 me-3 align-items-center text-bg-info border-0"
                   style={{zIndex: "1020"}} role="alert" aria-live="assertive" aria-atomic="true">
                <Toast.Body>
                    Job <strong><span>{updatedJob?.name}</span></strong> updated.
                    <CloseButton type="submit" variant="white" className="me-2 m-auto position-absolute end-0"
                                 aria-label="Close" onClick={() => setShowUpdatedToast(false)}/>
                </Toast.Body>
            </Toast>
        </>
    );
}

export type JobInfo = {
    id: 2,
    name: string,
    clazz: string,
    cronExpression?: string,
    cronActive: boolean,
    cronExpressionValid: boolean,
    forceRunnable: boolean
};

export type JobInfosContainer = {
    jobs: JobInfo[],
    latestRuns: { [k in number]: JobRunInfo }
};

export default List;
