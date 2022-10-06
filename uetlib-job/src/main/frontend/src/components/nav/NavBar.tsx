import Container from 'react-bootstrap/Container';
import Nav from 'react-bootstrap/Nav';
import Navbar from 'react-bootstrap/Navbar';
import {useEffect, useState} from "react";
import {loadApplicationName} from "../../dataProvider/dataProvider";

const NavBar = () => {
    const [applicationName, setApplicationName] = useState<string>();
    useEffect(() => {
        const fetchApplicationName = async () => {
            const name = await loadApplicationName();
            setApplicationName(name);
        };

        // noinspection JSIgnoredPromiseFromCall
        fetchApplicationName();
    }, []);

    const environmentClasses = () => {
        switch (applicationName?.split("-")?.pop()?.toLowerCase()) {
            case "prod":
                return "bg-danger text-light";
            case "dev":
                return "bg-info text-dark";
            case "local":
                return "bg-white text-dark";
            default:
                return "bg-warning text-light";
        }
    };

    return (
        <Navbar bg="dark" variant="dark" expand="md" sticky="top">
            <Container fluid>
                <Navbar.Brand href={process.env.PUBLIC_URL + "/"}>
                    <img
                        alt="Ubique Logo"
                        src={process.env.PUBLIC_URL + "/ub-logo-light.svg"}
                        height="30"
                        className="d-inline-block align-top"
                    />
                </Navbar.Brand>
                <Navbar.Toggle aria-controls="basic-navbar-nav"/>
                <Navbar.Collapse id="basic-navbar-nav">
                    <Nav activeKey={window.location.pathname} className="mx-auto">
                        <Nav.Link href={process.env.PUBLIC_URL + "/list"} className="fs-4">Job List</Nav.Link>
                        <Nav.Link href={process.env.PUBLIC_URL + "/runs"} className="fs-4">Job Runs</Nav.Link>
                    </Nav>
                    <Navbar.Text
                        className={`fs-6 px-3 py-2 badge rounded-pill ${environmentClasses()}`}>
                        {applicationName}
                    </Navbar.Text>
                </Navbar.Collapse>
            </Container>
        </Navbar>
    );
};

export default NavBar;
