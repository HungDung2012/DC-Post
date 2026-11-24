package com.dcpost.customer.security;

import com.dcpost.customer.auth.service.AuthService;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.*;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.web.filter.CorsFilter;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(InternalServiceProperties.class)
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain security(HttpSecurity http, AuthService auth, InternalServiceProperties internal,
                                 SecurityProblemWriter problems, Clock clock,
                                 @Value("${customer.cors-allowed-origins}") String allowedOrigins,
                                 @Value("${customer.auth.cookie-secure}") boolean cookieSecure) throws Exception {
        Set<String> origins = Arrays.stream(allowedOrigins.split(",")).map(String::strip)
                .filter(value -> !value.isEmpty()).collect(Collectors.toUnmodifiableSet());
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.copyOf(origins));
        cors.setAllowCredentials(true);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-XSRF-TOKEN", "Idempotency-Key"));
        cors.setExposedHeaders(List.of("X-Correlation-ID", "Retry-After"));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        var corsFilter = new CorsFilter(source);
        corsFilter.setCorsProcessor((configuration, request, response) -> {
            String origin = request.getHeader("Origin");
            if (origin != null && !origins.contains(origin)) {
                problems.write(request, response, HttpStatus.FORBIDDEN, "ORIGIN_REJECTED", "Nguồn yêu cầu không hợp lệ");
                return false;
            }
            return new DefaultCorsProcessor() {
                @Override
                protected void rejectRequest(org.springframework.http.server.ServerHttpResponse rejectedResponse)
                        throws java.io.IOException {
                    problems.write(request, response, HttpStatus.FORBIDDEN, "CORS_REJECTED", "Yêu cầu CORS không hợp lệ");
                }
            }.processRequest(configuration, request, response);
        });
        var csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieCustomizer(cookie -> cookie.path("/api/v1/auth").sameSite("Lax").secure(cookieSecure));
        var guard = new AuthRequestGuardFilter(origins, new RequestRateLimiter(clock, 10_000, 20, Duration.ofMinutes(1)), problems);

        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .httpBasic(basic -> basic.disable()).formLogin(form -> form.disable()).logout(logout -> logout.disable())
                .cors(configuration -> configuration.disable())
                .csrf(csrf -> csrf.csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .requireCsrfProtectionMatcher(request -> RequestPath.withinApplication(request).startsWith("/api/v1/auth/")
                                && !Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod())))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> problems.write(request, response,
                                HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Vui lòng đăng nhập"))
                        .accessDeniedHandler((request, response, exception) -> problems.write(request, response,
                                HttpStatus.FORBIDDEN, exception instanceof CsrfException ? "CSRF_INVALID" : "FORBIDDEN",
                                "Yêu cầu không được phép")))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/api/v1/auth/logout").hasRole("CUSTOMER")
                        .requestMatchers("/api/v1/auth/**", "/api/v1/public/**", "/api/v1/payments/vnpay/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/v1/customer/**").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/internal/shipments").hasAuthority("COUNTER_SHIPMENT_CREATE")
                        .requestMatchers(HttpMethod.POST, "/api/v1/internal/shipments/*/cash-confirmations").hasAuthority("CASH_PAYMENT_CONFIRM")
                        .anyRequest().denyAll())
                .addFilterBefore(corsFilter, CsrfFilter.class)
                .addFilterAfter(guard, CorsFilter.class)
                .addFilterBefore(new CustomerJwtFilter(auth, problems), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new InternalApiKeyFilter(internal, problems), CustomerJwtFilter.class);
        return http.build();
    }
}
