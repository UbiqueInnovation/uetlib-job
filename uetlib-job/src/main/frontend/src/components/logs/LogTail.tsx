import {LazyLog} from 'react-lazylog';

const LIVE_BASE_URL = process.env.REACT_APP_API_URL
    ? (
        process.env.REACT_APP_API_URL.startsWith("http")
            ? process.env.REACT_APP_API_URL.replace("http", "ws")
            : `ws://${process.env.REACT_APP_API_URL}`
    ) : (
        `${window.location.protocol === "https:" ? "wss:" : "ws:"}//${window.location.host}`
    );
const LIVE_URL = `${LIVE_BASE_URL}/v1/live`;

const LogTail = ({jobRunId, follow}: { jobRunId: number, follow: boolean }) => (
    <LazyLog
        url={`${LIVE_URL}/logs`}
        follow={follow}
        enableSearch
        selectableLines
        websocket
        websocketOptions={{
            onOpen: (_, sock) => {
                sock.send(JSON.stringify({jobRunId}))
            },
        }}
    />
);

export default LogTail;
