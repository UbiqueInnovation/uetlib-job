import {LazyLog} from 'react-lazylog';
import {hostBaseUrl} from "../../dataProvider/dataProvider";

const LIVE_BASE_URL = hostBaseUrl("ws");

const LogTail = ({jobRunId, follow}: { jobRunId: number, follow: boolean }) => (
    <LazyLog
        url={`${LIVE_BASE_URL}/logs`}
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
