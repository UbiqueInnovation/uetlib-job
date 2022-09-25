import {useLoaderData} from "react-router-dom";
import NavBar from "../nav/NavBar";
import Container from "react-bootstrap/Container";
import LogTail from "./LogTail";
import {Col, Form, Row} from "react-bootstrap";
import {useState} from "react";
import {JobRunInfoContainer} from "../runs/Runs";

const Logs = () => {
    const {jobRun, jobName} = useLoaderData() as JobRunInfoContainer;
    const [follow, setFollow] = useState(true);

    return (
        <>
            <NavBar/>
            <Container fluid className="text-light py-3 fs-5" style={{backgroundColor: "#222222"}}>
                <Container>
                    <Row xs={1} md={2}>
                        <Col>
                            Run {jobRun.id} of job {jobName}
                        </Col>
                        <Col>
                            <Form>
                                <Form.Check
                                    reverse
                                    type="switch"
                                    id="followSwitch"
                                    label="Auto-scroll to bottom"
                                    defaultChecked={true}
                                    onChange={(e) => setFollow(e.target.checked)}
                                />
                            </Form>
                        </Col>
                    </Row>
                </Container>
            </Container>
            <Container fluid className="px-0" style={{height: "100%"}}>
                <LogTail jobRunId={jobRun.id} follow={follow}/>
            </Container>
        </>
    );
}

export default Logs;
