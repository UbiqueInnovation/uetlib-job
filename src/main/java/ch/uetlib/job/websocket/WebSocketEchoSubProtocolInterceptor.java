package ch.uetlib.job.websocket;

import org.jspecify.annotations.Nullable;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * A {@link HandshakeInterceptor} that ensures a valid <strong>{@code
 * Sec-WebSocket-Protocol}</strong> header is echoed back to the browser during the WebSocket
 * handshake.
 *
 * <h2>Why you need it</h2>
 *
 * <ul>
 *   <li><strong>WebSocket protocol requirement&nbsp;&mdash;</strong> RFC&nbsp;6455 §1.9 mandates
 *       that, if the client offers one or more sub-protocols via the {@code Sec-WebSocket-Protocol}
 *       request header, the server <em>must</em> select (and echo) <em>exactly one</em> of those
 *       values in its handshake response. Browsers reject the connection if the header is missing
 *       or contains anything other than one of the originally proposed values.
 *   <li><strong>Bearer-token workaround&nbsp;&mdash;</strong> Some projects piggy-back on this
 *       header to pass an OAuth 2 access-token (for example, by sending <code>"Bearer,
 *       &lt;token&gt;"</code>). In that case the server still only needs to echo {@code "Bearer"}
 *       &mdash; not the token itself &mdash; to satisfy the browser.
 *   <li>Spring Framework does <em>not</em> automatically perform this echo, so an explicit
 *       interceptor is required.
 * </ul>
 *
 * <h2>Behaviour</h2>
 *
 * <pre>{@code
 * Client request header:  Sec-WebSocket-Protocol: Bearer, <accessToken>
 * Server response header: Sec-WebSocket-Protocol: Bearer
 * }</pre>
 *
 * <p>The interceptor:
 *
 * <ol>
 *   <li>Scans the incoming {@code Sec-WebSocket-Protocol} header.
 *   <li>If a value equal (case-insensitive) to {@code "Bearer"} is present, sets {@code "Bearer"}
 *       as the response header.
 *   <li>Leaves the handshake untouched if no suitable sub-protocol is offered (letting the browser
 *       decide whether to accept a “no-sub-protocol” handshake).
 * </ol>
 */
public class WebSocketEchoSubProtocolInterceptor implements HandshakeInterceptor {

    @Override
    @SuppressWarnings("java:S3516")
    public boolean beforeHandshake(
            final ServerHttpRequest request,
            final ServerHttpResponse response,
            final WebSocketHandler wsHandler,
            final Map<String, Object> attributes) {

        final var protocols = request.getHeaders().get("Sec-WebSocket-Protocol");
        if (protocols != null) {
            for (final var header : protocols) {
                for (final var offered : header.split(",", -1)) {
                    if ("Bearer".equalsIgnoreCase(offered.trim())) {
                        response.getHeaders().set("Sec-WebSocket-Protocol", "Bearer");
                        return true;
                    }
                }
            }
        }

        return true; // let the handshake proceed
    }

    @Override
    public void afterHandshake(
            final ServerHttpRequest request,
            final ServerHttpResponse response,
            final WebSocketHandler wsHandler,
            final @Nullable Exception exception) {
        // no-op
    }
}
