import {LazyLog} from 'react-lazylog';

const LogTail = ({jobRunId}: { jobRunId: number | null }) => (
    // <>
    // <ScrollFollow
    //     startFollowing={true}
    //     render={({follow, onScroll}) => (
    <LazyLog
        url="ws://localhost:8083/status/logs"
        follow={true}
        extraLines={1}
        enableSearch
        selectableLines
        websocket
        websocketOptions={{
            onOpen: (_e, sock) => {
                sock.send(JSON.stringify({jobRunId: 320}))
            },
            onError: e => console.log("onError", e),
            onClose: e => console.log("onClose", e),
        }}
        //{...onScroll}
    />
    // )}
    // />
    // </>
);

export default LogTail;
