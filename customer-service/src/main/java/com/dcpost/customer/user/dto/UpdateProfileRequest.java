package com.dcpost.customer.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(@NotBlank @Size(max = 255) String fullName,
                                   @NotBlank @Pattern(regexp = "0[0-9]{9,10}") String phone,
                                   @Size(max = 500) String address) {
    public UpdateProfileRequest {
        fullName = fullName == null ? null : fullName.strip();
        if (phone != null) {
            phone = phone.replaceAll("[\\s()-]", "");
            if (phone.startsWith("+84")) phone = "0" + phone.substring(3);
        }
        address = address == null || address.isBlank() ? null : address.strip();
    }
}
