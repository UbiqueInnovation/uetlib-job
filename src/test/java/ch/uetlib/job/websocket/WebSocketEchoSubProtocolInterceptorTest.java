package ch.uetlib.job.websocket;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;

class WebSocketEchoSubProtocolInterceptorTest {

    private static String handshake(final String... protocols) {
        final var request = new MockHttpServletRequest();
        for (final var protocol : protocols) {
            request.addHeader("Sec-WebSocket-Protocol", protocol);
        }
        final var response = new ServletServerHttpResponse(new MockHttpServletResponse());

        final var proceed =
                new WebSocketEchoSubProtocolInterceptor()
                        .beforeHandshake(
                                new ServletServerHttpRequest(request),
                                response,
                                mock(WebSocketHandler.class),
                                new HashMap<>());

        assertTrue(proceed);
        return response.getHeaders().getFirst("Sec-WebSocket-Protocol");
    }

    @Test
    void echoesBearerButNotTheToken() {
        assertEquals("Bearer", handshake("bearer, eyJhbGciOiJIUzI1NiJ9.e30.sig"));
        assertEquals("Bearer", handshake("chat", "Bearer,"));
    }

    @Test
    void leavesTheHandshakeUntouchedWithoutBearer() {
        assertNull(handshake("chat, superchat"));
        assertNull(handshake());
    }
}
