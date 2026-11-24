package com.dcpost.customer.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;

public record ChangePasswordRequest(
        @NotBlank @Size(max = 72) @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String currentPassword,
        @NotBlank @Size(min = 8, max = 72) @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String newPassword) {
    @AssertTrue(message = "Mật khẩu không được vượt quá 72 byte UTF-8")
    @JsonIgnore
    public boolean isPasswordWithinByteLimit() {
        return newPassword == null || newPassword.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
    @Override public String toString() { return "ChangePasswordRequest[redacted]"; }
}
