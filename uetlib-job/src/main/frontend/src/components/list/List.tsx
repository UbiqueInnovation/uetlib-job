import {useLoaderData} from "react-router-dom";
import {useEffect, useState} from "react";
import {formatTimestamp, JobRunInfo, jobRunStateToColor} from "../runs/Runs";
import NavBar from "../nav/NavBar";
import Container from "react-bootstrap/Container";
import {Button, Col, Modal, Row, Table, Toast} from "react-bootstrap";
import {startJob, updateJob} from "../../dataProvider/dataProvider";
import {find} from "lodash-es";

const List = () => {
    const [jobInfos, setJobInfos] = useState<JobInfo[]>([]);
    const [latestRuns, setLatestRuns] = useState<{ [k in number]: JobRunInfo }>({});

    const [showToast, setShowToast] = useState(false);
    const [showModal, setShowModal] = useState(false);
    const [triggeredJob, setTriggeredJob] = useState<JobInfo | undefined>(undefined);

    const jobInfosContainer = useLoaderData() as JobInfosContainer;

    useEffect(() => {
        setJobInfos(jobInfosContainer.jobs);
        setLatestRuns(jobInfosContainer.latestRuns);
    }, [jobInfosContainer]);

    const handleStartJob = (id: number, forceRun?: boolean) => {
        startJob(id, forceRun).then(started => {
            if (started) {
                setTriggeredJob(find(jobInfos, jobInfo => jobInfo.id === id));
                setShowToast(true);
            }
        });
    };

    const handleShowModal = (id: number) => {
        setTriggeredJob(find(jobInfos, jobInfo => jobInfo.id === id));
        setShowModal(true);
    };

    const handleStartJobModal = (forceRun?: boolean) => {
        triggeredJob && startJob(triggeredJob.id, forceRun).then(started => {
            if (started) {
                setShowModal(false);
                setShowToast(true);
            }
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
                                <th colSpan={2}>Cron</th>
                                <th>Started At</th>
                                <th>Finished At</th>
                                <th className="text-center">State</th>
                                <th className="text-center">Actions</th>
                            </tr>
                            </thead>
                            <tbody>
                            {jobInfos.map(jobInfo => (
                                <tr>
                                    <td className="align-middle text-center">{jobInfo.id}</td>
                                    <td className="align-middle">{jobInfo.name}</td>
                                    <td>
                                        <div className="input-group input-group-sm has-validation">
                                            <input type="text" placeholder="Cron expression"
                                                   id="cronExpression"
                                                   name="cronExpression" size={10}
                                                   className={"form-control" +
                                                       (jobInfo.cronExpressionValid ? "" : " is-invalid")}
                                                   value={jobInfo.cronExpression}/>
                                            <label htmlFor="cronExpression" hidden>Cron Expression</label>
                                            <button type="submit" className="btn btn-outline-secondary"
                                                    value="submit" onClick={() => updateJob(jobInfo.id)}>
                                                Save
                                            </button>
                                            {jobInfo.cronExpressionValid &&
                                                <div className="invalid-feedback">Invalid Expression</div>}
                                        </div>
                                    </td>
                                    <td className="align-middle text-center">
                                        {jobInfo.cronActive && <div className="badge bg-success">Active</div>}
                                    </td>
                                    <td className="align-middle">{latestRuns[jobInfo.id] &&
                                        formatTimestamp(latestRuns[jobInfo.id].startedAt)}</td>
                                    <td className="align-middle">{latestRuns[jobInfo.id]?.finishedAt &&
                                        formatTimestamp(latestRuns[jobInfo.id].finishedAt)}</td>
                                    <td className="align-middle text-center">
                                        {latestRuns[jobInfo.id] &&
                                            <span
                                                className={`badge bg-${jobRunStateToColor(latestRuns[jobInfo.id].state)}`}>{latestRuns[jobInfo.id].state}</span>
                                        }
                                    </td>
                                    <td className="align-middle text-center">
                                        {/* Job is NOT force runnable */}
                                        {!jobInfo.forceRunnable && (
                                            <button type="submit" className="btn btn-success btn-sm"
                                                    onClick={() => handleStartJob(jobInfo.id)}>
                                                <i className="bi bi-play-fill"></i> Start now
                                            </button>
                                        )}

                                        {/* Job is force runnable */}
                                        {jobInfo.forceRunnable && (
                                            <div>
                                                {/* Button trigger modal */}
                                                <button type="submit" className="btn btn-success btn-sm"
                                                        onClick={() => handleShowModal(jobInfo.id)}
                                                        data-bs-toggle="modal">
                                                    <i className="bi bi-play-fill"></i> Start now
                                                </button>

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
            <Toast show={showToast} onClose={() => setShowToast(false)}
                   className="position-fixed top-0 end-0 mt-5 me-3 align-items-center text-bg-success border-0"
                   style={{zIndex: "1020"}} role="alert" aria-live="assertive" aria-atomic="true">
                <Toast.Body>
                    Job <strong><span>{triggeredJob?.name}</span></strong> triggered.
                    <button type="button" className="btn-close btn-close-white me-2 m-auto position-absolute end-0"
                            aria-label="Close" onClick={() => setShowToast(false)}></button>
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
