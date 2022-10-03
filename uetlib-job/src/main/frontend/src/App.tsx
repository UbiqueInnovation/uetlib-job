import React from 'react';
import './App.css';
import LogTail from "./components/LogTail";

const App = ({jobRunId}: { jobRunId: number | null }) => (
    <div style={{width: "100vw", height: "100vh"}}>
        <LogTail jobRunId={jobRunId}/>
    </div>
);

export default App;
