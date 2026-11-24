package com.dcpost.customer.auth;

import com.dcpost.customer.auth.controller.AuthController;
import com.dcpost.customer.auth.dto.CustomerAccount;
import com.dcpost.customer.auth.service.RegistrationService;
import com.dcpost.customer.common.exception.AccountConflictException;
import com.dcpost.customer.common.exception.ApiExceptionHandler;
import com.dcpost.customer.common.web.CorrelationIdFilter;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RegistrationHttpTest {
    private final RegistrationService registration = mock(RegistrationService.class);
    private MockMvc mvc;
    private static final String BODY = """
            {"fullName":"Nguyen An","email":"an@example.com","phone":"0912345678",
             "password":"safe-password","roles":["ADMIN"]}
            """;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(registration))
                .addFilter(new CorrelationIdFilter())
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    /** Endpoint đăng ký trả 201 và DTO công khai không chứa hash hoặc password. */
    @Test
    void Test_register_001() throws Exception {
        when(registration.register(any())).thenReturn(new CustomerAccount(1L, "Nguyen An",
                "an@example.com", "0912345678", Set.of("CUSTOMER")));
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated()).andExpect(jsonPath("roles[0]").value("CUSTOMER"))
                .andExpect(jsonPath("password").doesNotExist())
                .andExpect(jsonPath("passwordHash").doesNotExist())
                .andExpect(jsonPath("refreshTokenHash").doesNotExist());
    }

    /** Trùng thông tin trả Problem Details 409, không lộ chi tiết SQL. */
    @Test
    void Test_register_002() throws Exception {
        when(registration.register(any())).thenThrow(new AccountConflictException());
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("ACCOUNT_CONFLICT"))
                .andExpect(jsonPath("correlationId").isNotEmpty())
                .andExpect(header().exists("X-Correlation-ID"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    /** Validation HTTP trả danh sách field errors, không echo giá trị mật khẩu. */
    @Test
    void Test_register_003() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("safe-password", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("errors.password").exists());
        verifyNoInteractions(registration);
    }
}
