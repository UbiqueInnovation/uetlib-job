package ch.uetlib.job.config;

import static org.junit.jupiter.api.Assertions.*;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.time.Instant;

import javax.crypto.spec.SecretKeySpec;

class UetlibJobSecurityConfigTest {

    @Test
    void statusDecoderAcceptsTokensSignedWithTheUtf8SecretBytes() {
        final var secret = "status-interface-secret-für-tests-0123456789";
        final var key = new SecretKeySpec(secret.getBytes(UTF_8), "HmacSHA256");
        final var token =
                new NimbusJwtEncoder(new ImmutableSecret<>(key))
                        .encode(
                                JwtEncoderParameters.from(
                                        JwsHeader.with(MacAlgorithm.HS256).build(),
                                        JwtClaimsSet.builder()
                                                .subject("status")
                                                .expiresAt(Instant.now().plusSeconds(60))
                                                .build()))
                        .getTokenValue();

        final var jwt =
                new UetlibJobSecurityConfig(secret).uetlibJobStatusJwtDecoder().decode(token);

        assertEquals("status", jwt.getSubject());
    }
}
