package com.dcpost.customer.user;

import com.dcpost.customer.CustomerApplication;
import com.dcpost.customer.auth.dto.AuthSession;
import com.dcpost.customer.auth.dto.RegisterRequest;
import com.dcpost.customer.auth.service.AuthService;
import com.dcpost.customer.auth.service.RegistrationService;
import com.dcpost.customer.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = CustomerApplication.class)
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
class ProfileHttpIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RegistrationService registration;
    @Autowired AuthService auth;
    @Autowired UserRepository users;
    private long userId;
    private AuthSession session;

    @BeforeEach
    void register() {
        String email = "profile-" + UUID.randomUUID() + "@example.invalid";
        String phone = "09" + String.format("%08d", Math.floorMod(System.nanoTime(), 100_000_000));
        userId = registration.register(new RegisterRequest("Profile test", email, phone, "safe-password")).id();
        session = auth.login(email, "safe-password");
    }

    @AfterEach void cleanUp() { users.deleteById(userId); }

    /** GET profile chỉ lấy user từ Bearer principal và không trả dữ liệu xác thực. */
    @Test
    void Test_getProfile_001() throws Exception {
        mvc.perform(get("/api/v1/customer/profile").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("id").value(userId))
                .andExpect(jsonPath("passwordHash").doesNotExist())
                .andExpect(jsonPath("refreshTokenHash").doesNotExist())
                .andExpect(jsonPath("authVersion").doesNotExist());
    }

    /** User ID giả trong body không đổi chủ sở hữu; email đăng nhập không sửa qua profile. */
    @Test
    void Test_updateProfile_001() throws Exception {
        mvc.perform(put("/api/v1/customer/profile").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"userId":999999,"fullName":"Updated profile","phone":"0900000099",
                         "address":"New address","email":"ignored@example.invalid"}
                        """))
                .andExpect(status().isOk()).andExpect(jsonPath("id").value(userId))
                .andExpect(jsonPath("fullName").value("Updated profile"));
        var user = users.findById(userId).orElseThrow();
        assertEquals("Updated profile", user.getFullName());
        assertEquals(session.account().email(), user.getEmail());
    }

    /** Sai mật khẩu hiện tại trả lỗi riêng và không thu hồi phiên đang dùng. */
    @Test
    void Test_changePassword_001() throws Exception {
        mvc.perform(post("/api/v1/customer/change-password").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"currentPassword":"wrong-password","newPassword":"new-password"}
                        """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("CURRENT_PASSWORD_INVALID"));
        assertEquals(userId, auth.authenticate(session.accessToken()).userId());
    }

    /** Đổi mật khẩu xóa cookie, thu hồi access/refresh cũ và mật khẩu mới đăng nhập được. */
    @Test
    void Test_changePassword_002() throws Exception {
        mvc.perform(post("/api/v1/customer/change-password").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"currentPassword":"safe-password","newPassword":"new-password"}
                        """))
                .andExpect(status().isNoContent()).andExpect(cookie().maxAge("refresh_token", 0));
        mvc.perform(get("/api/v1/customer/profile").header("Authorization", bearer()))
                .andExpect(status().isUnauthorized());
        assertThrows(org.springframework.security.authentication.BadCredentialsException.class,
                () -> auth.refresh(session.refreshToken()));
        assertNotNull(auth.login(session.account().email(), "new-password"));
    }

    private String bearer() { return "Bearer " + session.accessToken(); }
}
