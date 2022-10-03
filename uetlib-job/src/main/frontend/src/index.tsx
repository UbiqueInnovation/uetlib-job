import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import {createBrowserRouter, Navigate, RouterProvider,} from "react-router-dom";
import {loadJobs, loadRun, loadRuns} from "./dataProvider/dataProvider";
import List from "./components/list/List";
import Runs from "./components/runs/Runs";
import Logs from "./components/logs/Logs";

const router = createBrowserRouter([
    {
        path: "/",
        element: <Navigate to="/list" replace={true}/>
    },
    {
        path: "/list",
        element: <List/>,
        loader: loadJobs,
    },
    {
        path: "/runs",
        element: <Runs/>,
        loader: loadRuns,
    },
    {
        path: "/logs/:jobRunId",
        element: <Logs/>,
        loader: ({params}) => loadRun(parseInt(params.jobRunId as string)),
    }
], {
    basename: "/status"
});

const root = ReactDOM.createRoot(document.getElementById('root') as HTMLElement);
root.render(
    // <React.StrictMode> // causes websocket to load twice and fail
    <RouterProvider router={router}/>
    // </React.StrictMode>
);
