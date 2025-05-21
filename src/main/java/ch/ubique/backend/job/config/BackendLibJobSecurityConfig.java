package ch.ubique.backend.job.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;

@Profile("!no-security")
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@ConditionalOnWebApplication
public class BackendLibJobSecurityConfig {

    @Bean
    public JwtDecoder backendLibJobStatusJwtDecoder(
            @Value("${ch.ubique.backend.job.interface.secret}") final String secret) {
        return NimbusJwtDecoder.withSecretKey(new SecretKeySpec(secret.getBytes(), "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain bearerFilterChain(
            final HttpSecurity http,
            @Qualifier("backendLibJobStatusJwtDecoder")
                    final JwtDecoder backendLibJobStatusJwtDecoder)
            throws Exception {
        http.securityMatcher("/v1/status/**")
                .authorizeHttpRequests(
                        authorize ->
                                authorize
                                        .requestMatchers(HttpMethod.OPTIONS, "/v1/status/**")
                                        .permitAll()
                                        .requestMatchers("/v1/status/**")
                                        .authenticated())
                .oauth2ResourceServer(
                        customizer ->
                                customizer.jwt(jwt -> jwt.decoder(backendLibJobStatusJwtDecoder)));

        return http.build();
    }
}
