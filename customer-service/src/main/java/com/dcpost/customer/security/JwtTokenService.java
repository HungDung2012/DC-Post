package com.dcpost.customer.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {
    private static final String ISSUER = "dcpost-customer";
    private final NimbusJwtEncoder encoder;
    private final NimbusJwtDecoder decoder;
    private final Duration accessDuration;
    public record AccessIdentity(long userId, long authVersion, Instant issuedAt, Instant expiresAt) {}

    public JwtTokenService(@Value("${customer.auth.jwt-secret}") String secret,
                           @Value("${customer.auth.access-token-duration}") Duration accessDuration, Clock clock) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) throw new IllegalArgumentException("JWT secret must contain at least 32 UTF-8 bytes");
        if (accessDuration.isNegative() || accessDuration.isZero()) {
            throw new IllegalArgumentException("Access token duration must be positive");
        }
        var key = new SecretKeySpec(bytes, "HmacSHA256");
        encoder = new NimbusJwtEncoder(new com.nimbusds.jose.jwk.source.ImmutableSecret<>(key));
        decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        var claimConverter = MappedJwtClaimSetConverter.withDefaults(java.util.Map.of());
        decoder.setClaimSetConverter(claims -> {
            // Spring's default converter synthesizes iat from exp when it is absent.
            if (claims.get("iat") == null || claims.get("exp") == null) {
                throw new BadJwtException("Missing access token timestamps");
            }
            return claimConverter.convert(claims);
        });
        var timestamp = new JwtTimestampValidator(Duration.ZERO);
        timestamp.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamp, new JwtIssuerValidator(ISSUER)));
        this.accessDuration = accessDuration;
    }

    public String issue(long userId, long authVersion, Instant issuedAt) {
        var claims = JwtClaimsSet.builder().issuer(ISSUER).subject(Long.toString(userId))
                .issuedAt(issuedAt).notBefore(issuedAt).expiresAt(issuedAt.plus(accessDuration))
                .claim("auth_version", authVersion).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    public AccessIdentity verify(String token) {
        var jwt = decoder.decode(token);
        try {
            long userId = Long.parseLong(jwt.getSubject());
            Object versionClaim = jwt.getClaims().get("auth_version");
            if (!(versionClaim instanceof Long || versionClaim instanceof Integer)) {
                throw new IllegalArgumentException("Invalid session version");
            }
            long version = ((Number) versionClaim).longValue();
            if (userId <= 0 || version < 0 || jwt.getIssuedAt() == null || jwt.getExpiresAt() == null) {
                throw new IllegalArgumentException("Invalid access identity");
            }
            return new AccessIdentity(userId, version, jwt.getIssuedAt(), jwt.getExpiresAt());
        } catch (IllegalArgumentException exception) {
            throw new BadJwtException("Invalid access identity", exception);
        }
    }
}
