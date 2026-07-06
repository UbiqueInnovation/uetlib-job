package ch.ubique.backend.job.websocket;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.Strings;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.BearerTokenErrors;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * A {@link BearerTokenResolver} implementation that resolves Bearer tokens from WebSocket handshake
 * requests via the {@code Sec-WebSocket-Protocol} header and traditional HTTP requests via the
 * {@code Authorization} header.
 *
 * <p>This class is responsible for handling WebSocket-specific token resolution where the token is
 * passed as a subprotocol in the {@code Sec-WebSocket-Protocol} header. In the case of WebSocket
 * requests, the header is expected to contain the format:
 *
 * <pre>{@code
 * Sec-WebSocket-Protocol: Bearer, <access-token>
 * }</pre>
 *
 * <p>This class will extract the access token after the {@code Bearer} keyword, which is used for
 * authenticating the WebSocket connection.
 *
 * <p>If no token is found in the WebSocket protocol header, the class will fall back to resolving
 * the token from the traditional {@code Authorization} header used in HTTP requests.
 *
 * <h2>Functionality</h2>
 *
 * <ol>
 *   <li>Resolves tokens from the {@code Sec-WebSocket-Protocol} header for WebSocket handshake
 *       requests.
 *   <li>Falls back to resolving tokens from the traditional {@code Authorization} header for HTTP
 *       requests.
 *   <li>Ensures that multiple token sources do not conflict by throwing an exception if more than
 *       one valid token is found.
 * </ol>
 *
 * <h3>Example WebSocket Request</h3>
 *
 * <pre>{@code
 * GET /ws HTTP/1.1
 * Host: example.com
 * Upgrade: websocket
 * Connection: Upgrade
 * Sec-WebSocket-Key: <key>
 * Sec-WebSocket-Version: 13
 * Sec-WebSocket-Protocol: Bearer, <access-token>
 * }</pre>
 *
 * <p>In this case, the token would be extracted from the {@code Sec-WebSocket-Protocol} header.
 *
 * <h3>Example HTTP Request</h3>
 *
 * <pre>{@code
 * GET /api/resource HTTP/1.1
 * Host: example.com
 * Authorization: Bearer <access-token>
 * }</pre>
 *
 * <p>The token would be extracted from the {@code Authorization} header.
 *
 * @since 1.0
 */
public final class WebSocketSubProtocolBearerTokenResolver implements BearerTokenResolver {

    private static final Pattern authorizationPattern =
            Pattern.compile("^Bearer (?<token>[a-zA-Z0-9-._~+/]+=*)$", Pattern.CASE_INSENSITIVE);

    @Override
    @Nullable
    public String resolve(final HttpServletRequest request) {
        return resolveToken(
                // 1. First, try resolving token from the WebSocket header (Sec-WebSocket-Protocol)
                resolveFromWebSocketProtocol(request),
                // 2. Fallback to resolve from Authorization header
                resolveFromAuthorizationHeader(request));
    }

    /**
     * Resolves the token from the {@code Sec-WebSocket-Protocol} header (if present).
     *
     * <p>This method checks the WebSocket handshake headers for the presence of a subprotocol that
     * starts with the keyword {@code "Bearer"} and extracts the token from that header if found.
     *
     * <p>The header must be formatted as:
     *
     * <pre>{@code
     * Sec-WebSocket-Protocol: Bearer, <access-token>
     * }</pre>
     *
     * <p>If the header is valid and contains the {@code "Bearer"} token, the token is extracted
     * after the comma.
     *
     * @param request the HTTP request (WebSocket handshake request)
     * @return the token if found in the {@code Sec-WebSocket-Protocol} header, otherwise null
     */
    @Nullable
    private String resolveFromWebSocketProtocol(final HttpServletRequest request) {
        final var protocolHeader = request.getHeader("Sec-WebSocket-Protocol");

        if (protocolHeader == null) {
            return null;
        }

        // Split the protocols and strip whitespace
        final var protocols =
                Arrays.stream(protocolHeader.split(","))
                        .map(String::strip) // Strip leading/trailing spaces
                        .toList();

        // Check if the first protocol is "Bearer"
        if (protocols.size() == 2 && "Bearer".equalsIgnoreCase(protocols.get(0))) {
            // Return the token, which is the second part after the comma
            return protocols.get(1);
        }
        return null;
    }

    /**
     * Resolves the token from the {@code Authorization} header (if present).
     *
     * <p>This method checks the {@code Authorization} header for a valid Bearer token, and if
     * found, extracts the token. If the header is malformed or the token is not valid, an {@link
     * OAuth2AuthenticationException} is thrown.
     *
     * @param request the HTTP request (potentially containing a Bearer token in the Authorization
     *     header)
     * @return the token if found in the Authorization header, otherwise null
     */
    @Nullable
    private String resolveFromAuthorizationHeader(final HttpServletRequest request) {
        final var authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!Strings.CI.startsWith(authorization, "bearer")) {
            return null;
        }

        final var matcher = authorizationPattern.matcher(authorization);
        if (!matcher.matches()) {
            final var error = BearerTokenErrors.invalidToken("Bearer token is malformed");
            throw new OAuth2AuthenticationException(error);
        }

        return matcher.group("token");
    }

    /**
     * Resolves the token from multiple possible sources (WebSocket header and Authorization
     * header).
     *
     * <p>This method ensures that only one valid token is found. If multiple tokens are found, an
     * {@link OAuth2AuthenticationException} is thrown.
     *
     * @param accessTokens the token values to resolve
     * @return the resolved token
     * @throws OAuth2AuthenticationException if multiple tokens are found or the token is empty
     */
    @Nullable
    private static String resolveToken(final @Nullable String @Nullable ... accessTokens) {
        if (accessTokens == null || accessTokens.length == 0) {
            return null;
        }

        String accessToken = null;
        for (final var token : accessTokens) {
            if (accessToken == null) {
                accessToken = token;
            } else if (token != null) {
                final var error =
                        BearerTokenErrors.invalidRequest(
                                "Found multiple bearer tokens in the request");
                throw new OAuth2AuthenticationException(error);
            }
        }

        if (accessToken != null && accessToken.isBlank()) {
            final var error =
                    BearerTokenErrors.invalidRequest(
                            "The requested token parameter is an empty string");
            throw new OAuth2AuthenticationException(error);
        }

        return accessToken;
    }
}
