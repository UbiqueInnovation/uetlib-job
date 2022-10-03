import {LazyLog} from 'react-lazylog';
import {hostBaseUrl} from "../../dataProvider/dataProvider";

const LIVE_BASE_URL = hostBaseUrl(window.location.protocol === "https:" ? "wss:" : "ws:");
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
