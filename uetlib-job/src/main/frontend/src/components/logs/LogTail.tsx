import {LazyLog} from 'react-lazylog';

const LogTail = ({jobRunId, follow}: { jobRunId: number, follow: boolean }) => (
    <LazyLog
        url="ws://localhost:8083/v1/live/logs"
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
