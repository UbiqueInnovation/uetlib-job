import {JobRunInfo, jobRunStateToColor} from "../runs/Runs";
import {Badge, Button} from "react-bootstrap";
import React from "react";
import {useNavigate} from "react-router-dom";

const ShowLogsButton = ({jobRunInfo}: { jobRunInfo: JobRunInfo }) => {
    const navigate = useNavigate();
    const jobRunStateColor = jobRunStateToColor(jobRunInfo.state);

    return (
        <Button type="submit" size="sm" variant={`outline-${jobRunStateColor}`}
                onClick={() => navigate(`../logs/${jobRunInfo.id}`)}>
            <i className="bi bi-file-text"></i>
            <span> Show Logs </span>
            <Badge bg={jobRunStateColor}>{jobRunInfo.state}</Badge>
        </Button>
    );
};

export default ShowLogsButton;
