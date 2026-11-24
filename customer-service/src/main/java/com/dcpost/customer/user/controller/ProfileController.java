package com.dcpost.customer.user.controller;

import com.dcpost.customer.security.CustomerPrincipal;
import com.dcpost.customer.user.dto.ChangePasswordRequest;
import com.dcpost.customer.user.dto.CustomerProfile;
import com.dcpost.customer.user.dto.UpdateProfileRequest;
import com.dcpost.customer.user.service.ProfileService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customer")
public class ProfileController {
    private final ProfileService profiles;
    private final boolean cookieSecure;

    public ProfileController(ProfileService profiles, @Value("${customer.auth.cookie-secure}") boolean cookieSecure) {
        this.profiles = profiles;
        this.cookieSecure = cookieSecure;
    }

    @GetMapping("/profile")
    public CustomerProfile getProfile(@AuthenticationPrincipal CustomerPrincipal principal) {
        return profiles.getProfile(principal.userId());
    }

    @PutMapping("/profile")
    public CustomerProfile updateProfile(@AuthenticationPrincipal CustomerPrincipal principal,
                                          @Valid @RequestBody UpdateProfileRequest request) {
        return profiles.updateProfile(principal.userId(), request);
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal CustomerPrincipal principal,
                               @Valid @RequestBody ChangePasswordRequest request, HttpServletResponse response) {
        profiles.changePassword(principal.userId(), request.currentPassword(), request.newPassword());
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("refresh_token", "").httpOnly(true)
                .secure(cookieSecure).sameSite("Lax").path("/api/v1/auth").maxAge(Duration.ZERO).build().toString());
    }
}
