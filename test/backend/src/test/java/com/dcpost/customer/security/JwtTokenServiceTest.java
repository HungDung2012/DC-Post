package com.dcpost.customer.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenServiceTest {
    private static final String SECRET = "isolated-jwt-test-signing-secret-at-least-32-bytes";
    private static final Instant NOW = Instant.parse("2026-11-24T10:00:00Z");

    private JwtTokenService service(Instant now) {
        return new JwtTokenService(SECRET, Duration.ofHours(24), Clock.fixed(now, ZoneOffset.UTC));
    }

    /** Token có chữ ký hợp lệ giữ user/version và hết hạn sau đúng 24 giờ. */
    @Test
    void Test_issue_001() {
        var jwt = service(NOW);
        var identity = jwt.verify(jwt.issue(42, 3, NOW));
        assertNotNull(identity);
        assertEquals(42, identity.userId());
        assertEquals(3, identity.authVersion());
        assertEquals(NOW, identity.issuedAt());
        assertEquals(NOW.plus(Duration.ofDays(1)), identity.expiresAt());
    }

    /** Token đã quá hạn bị từ chối khi kiểm tra chữ ký và thời gian. */
    @Test
    void Test_verify_001() {
        String token = service(NOW).issue(42, 3, NOW);
        assertThrows(JwtException.class, () -> service(NOW.plusSeconds(86401)).verify(token));
    }

    /** Backend dùng secret khác không chấp nhận chữ ký token. */
    @Test
    void Test_verify_002() {
        String token = service(NOW).issue(42, 3, NOW);
        var other = new JwtTokenService("another-isolated-test-signing-secret-at-least-32-bytes",
                Duration.ofHours(24), Clock.fixed(NOW, ZoneOffset.UTC));
        assertThrows(JwtException.class, () -> other.verify(token));
    }

    /** Chuỗi không phải JWT không được chấp nhận. */
    @Test
    void Test_verify_003() {
        assertThrows(JwtException.class, () -> service(NOW).verify("not-a-jwt"));
    }

    /** Token chưa đến thời điểm hiệu lực bị từ chối. */
    @Test
    void Test_verify_004() {
        String token = service(NOW).issue(42, 3, NOW.plusSeconds(60));
        assertThrows(JwtException.class, () -> service(NOW).verify(token));
    }

    /** Chữ ký đúng nhưng issuer khác vẫn bị từ chối. */
    @Test
    void Test_verify_005() throws Exception {
        assertThrows(JwtException.class, () -> service(NOW).verify(signed("iss", "other-service")));
    }

    /** Chữ ký đúng nhưng thiếu thời điểm hết hạn vẫn bị từ chối. */
    @Test
    void Test_verify_006() throws Exception {
        assertThrows(JwtException.class, () -> service(NOW).verify(signed("exp", null)));
    }

    /** Chữ ký đúng nhưng thiếu thời điểm phát hành vẫn bị từ chối. */
    @Test
    void Test_verify_007() throws Exception {
        assertThrows(JwtException.class, () -> service(NOW).verify(signed("iat", null)));
    }

    /** Subject không phải user ID dương vẫn bị từ chối dù chữ ký hợp lệ. */
    @Test
    void Test_verify_008() throws Exception {
        assertThrows(JwtException.class, () -> service(NOW).verify(signed("sub", "-1")));
    }

    /** Session version phải là số nguyên không âm, không nhận chuỗi hoặc số lẻ. */
    @Test
    void Test_verify_009() throws Exception {
        assertThrows(JwtException.class, () -> service(NOW).verify(signed("auth_version", "3")));
        assertThrows(JwtException.class, () -> service(NOW).verify(signed("auth_version", 1.5)));
        assertThrows(JwtException.class, () -> service(NOW).verify(signed("auth_version", -1L)));
    }

    private String signed(String changedClaim, Object value) throws Exception {
        var claims = new java.util.HashMap<String, Object>();
        claims.put("iss", "dcpost-customer");
        claims.put("sub", "42");
        claims.put("iat", java.util.Date.from(NOW));
        claims.put("nbf", java.util.Date.from(NOW));
        claims.put("exp", java.util.Date.from(NOW.plusSeconds(86400)));
        claims.put("auth_version", 3L);
        if (value == null) claims.remove(changedClaim); else claims.put(changedClaim, value);
        var builder = new com.nimbusds.jwt.JWTClaimsSet.Builder();
        claims.forEach(builder::claim);
        var jwt = new com.nimbusds.jwt.SignedJWT(new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.HS256),
                builder.build());
        jwt.sign(new com.nimbusds.jose.crypto.MACSigner(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}
