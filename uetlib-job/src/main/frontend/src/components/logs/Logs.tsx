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
                    <Row className="g-3 mx-auto">
                        <Col md={6} lg={{span: 5, offset: 1}} xl={{span: 4, offset: 2}} xxl={{span: 3, offset: 3}} className="text-nowrap">
                            Run {jobRun.id} of job {jobName}
                        </Col>
                        <Col md={6} lg={5} xl={4} xxl={3} className="text-nowrap">
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
