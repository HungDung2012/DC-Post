package com.dcpost.customer.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public record RegisterRequest(
        @NotBlank @Size(max = 255) String fullName,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Pattern(regexp = "0[0-9]{9,10}") String phone,
        @NotBlank @Size(min = 8, max = 72)
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String password) {

    public RegisterRequest {
        fullName = fullName == null ? null : fullName.strip();
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
        if (phone != null) {
            phone = phone.replaceAll("[\\s()-]", "");
            if (phone.startsWith("+84")) phone = "0" + phone.substring(3);
        }
    }

    @AssertTrue(message = "Mật khẩu không được vượt quá 72 byte UTF-8")
    @JsonIgnore
    public boolean isPasswordWithinByteLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    @Override
    public String toString() { return "RegisterRequest[redacted]"; }
}
