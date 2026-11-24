package com.dcpost.customer.security;

import com.dcpost.customer.CustomerApplication;
import com.dcpost.customer.auth.dto.RegisterRequest;
import com.dcpost.customer.auth.service.AuthService;
import com.dcpost.customer.auth.service.RegistrationService;
import com.dcpost.customer.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = CustomerApplication.class, properties = {
        "customer.internal.services.counter.api-key=isolated-counter-test-key",
        "customer.internal.services.cashier.api-key=isolated-cashier-test-key"})
@AutoConfigureMockMvc(print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
@ActiveProfiles({"test", "security-boundary"})
@Import(SecurityBoundaryIT.ProbeController.class)
class SecurityBoundaryIT {
    private static final String ORIGIN = "http://localhost:5173";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RegistrationService registration;
    @Autowired AuthService auth;
    @Autowired UserRepository users;
    private Long userId;
    private String email;

    @AfterEach
    void cleanUp() { if (userId != null) users.deleteById(userId); }

    /** Customer API thiếu Bearer token trả Problem Details 401. */
    @Test
    void Test_security_001() throws Exception {
        mvc.perform(get("/api/v1/customer/security-probe"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("code").value("UNAUTHENTICATED"));
    }

    /** Access token hợp lệ được kiểm tra lại tài khoản rồi mới vào Customer API. */
    @Test
    void Test_security_002() throws Exception {
        var session = login();
        mvc.perform(get("/api/v1/customer/security-probe").header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().isOk());
        auth.login(email, "safe-password");
        mvc.perform(get("/api/v1/customer/security-probe").header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().isUnauthorized());
    }

    /** Bearer bị sửa không được dùng truy cập dữ liệu riêng tư. */
    @Test
    void Test_security_003() throws Exception {
        mvc.perform(get("/api/v1/customer/security-probe").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("code").value("UNAUTHENTICATED"));
    }

    /** CUSTOMER không được dùng JWT thay API key nội bộ. */
    @Test
    void Test_security_004() throws Exception {
        var session = login();
        mvc.perform(post("/api/v1/internal/shipments").header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().isUnauthorized());
    }

    /** API key từng service chỉ được thực hiện thao tác đã cấp quyền. */
    @Test
    void Test_security_005() throws Exception {
        mvc.perform(post("/api/v1/internal/shipments").header("X-API-Key", "isolated-cashier-test-key"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/internal/shipments").header("X-API-Key", "isolated-counter-test-key"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/internal/shipments/123/cash-confirmations")
                        .header("X-API-Key", "isolated-cashier-test-key"))
                .andExpect(status().isOk());
    }

    /** Refresh dùng cookie nhưng thiếu CSRF bị từ chối. */
    @Test
    void Test_security_006() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh").header("Origin", ORIGIN))
                .andExpect(status().isForbidden()).andExpect(jsonPath("code").value("CSRF_INVALID"));
    }

    /** Origin ngoài allowlist không được thay đổi phiên. */
    @Test
    void Test_security_007() throws Exception {
        mvc.perform(post("/api/v1/auth/login").header("Origin", "https://untrusted.invalid")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("code").value("ORIGIN_REJECTED"));
    }

    /** Login cấp refresh cookie HttpOnly/SameSite/Path và không trả refresh trong JSON. */
    @Test
    void Test_security_008() throws Exception {
        login();
        var csrf = csrf();
        mvc.perform(post("/api/v1/auth/login").header("Origin", ORIGIN).cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("email", email, "password", "safe-password"))))
                .andExpect(status().isOk()).andExpect(cookie().httpOnly("refresh_token", true))
                .andExpect(cookie().path("refresh_token", "/api/v1/auth"))
                .andExpect(header().stringValues("Set-Cookie", org.hamcrest.Matchers.hasItem(containsString("SameSite=Lax"))))
                .andExpect(jsonPath("accessToken").isNotEmpty())
                .andExpect(jsonPath("refreshToken").doesNotExist());
    }

    /** Origin bị thiếu cũng không được thay đổi phiên từ cookie. */
    @Test
    void Test_security_009() throws Exception {
        var csrf = csrf();
        mvc.perform(post("/api/v1/auth/refresh").cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token()))
                .andExpect(status().isForbidden());
    }

    /** Refresh qua HTTP xoay cookie, token cũ bị từ chối và logout thu hồi access. */
    @Test
    void Test_security_010() throws Exception {
        var original = login();
        var csrf = csrf();
        var refreshed = mvc.perform(post("/api/v1/auth/refresh").header("Origin", ORIGIN)
                        .cookie(csrf.cookie(), new Cookie("refresh_token", original.refreshToken()))
                        .header("X-XSRF-TOKEN", csrf.token()))
                .andExpect(status().isOk()).andExpect(cookie().httpOnly("refresh_token", true))
                .andReturn().getResponse();
        String access = json.readTree(refreshed.getContentAsString()).get("accessToken").asText();
        mvc.perform(post("/api/v1/auth/refresh").header("Origin", ORIGIN)
                        .cookie(csrf.cookie(), new Cookie("refresh_token", original.refreshToken()))
                        .header("X-XSRF-TOKEN", csrf.token()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/logout").header("Origin", ORIGIN).cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token()).header("Authorization", "Bearer " + access))
                .andExpect(status().isNoContent()).andExpect(cookie().maxAge("refresh_token", 0));
        mvc.perform(get("/api/v1/customer/security-probe").header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized());
    }

    /** Preflight của website cho phép credential và không dùng wildcard Origin. */
    @Test
    void Test_security_011() throws Exception {
        mvc.perform(options("/api/v1/auth/login").header("Origin", ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type,X-XSRF-TOKEN"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    /** Rate limit theo IP trực tiếp; thay X-Forwarded-For không vượt được hạn mức. */
    @Test
    void Test_security_012() throws Exception {
        for (int index = 0; index < 20; index++) {
            mvc.perform(post("/api/v1/auth/login").header("Origin", ORIGIN)
                            .header("X-Forwarded-For", "203.0.113." + index)
                            .with(request -> { request.setRemoteAddr("192.0.2.100"); return request; }))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/v1/auth/login").header("Origin", ORIGIN)
                        .with(request -> { request.setRemoteAddr("192.0.2.100"); return request; }))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("code").value("RATE_LIMITED"));
    }

    /** Preflight dùng header không cho phép vẫn trả Problem Details và correlation ID. */
    @Test
    void Test_security_013() throws Exception {
        mvc.perform(options("/api/v1/auth/login").header("Origin", ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "X-API-Key"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("code").value("CORS_REJECTED"))
                .andExpect(jsonPath("correlationId").isNotEmpty());
    }

    private Csrf csrf() throws Exception {
        var response = mvc.perform(get("/api/v1/auth/csrf").header("Origin", ORIGIN))
                .andExpect(status().isOk()).andReturn().getResponse();
        return new Csrf(response.getCookie("XSRF-TOKEN"), json.readTree(response.getContentAsString()).get("token").asText());
    }

    private com.dcpost.customer.auth.dto.AuthSession login() {
        email = "security-" + UUID.randomUUID() + "@example.invalid";
        String phone = "09" + String.format("%08d", Math.floorMod(System.nanoTime(), 100_000_000));
        userId = registration.register(new RegisterRequest("Security test", email, phone, "safe-password")).id();
        return auth.login(email, "safe-password");
    }

    private record Csrf(Cookie cookie, String token) {}

    @RestController
    @Profile("security-boundary")
    static class ProbeController {
        @GetMapping("/api/v1/customer/security-probe") String customer() { return "ok"; }
        @PostMapping("/api/v1/internal/shipments") String counter() { return "ok"; }
        @PostMapping("/api/v1/internal/shipments/{id}/cash-confirmations") String cash() { return "ok"; }
    }
}
