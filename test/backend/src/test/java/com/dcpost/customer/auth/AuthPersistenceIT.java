package com.dcpost.customer.auth;

import com.dcpost.customer.CustomerApplication;
import com.dcpost.customer.auth.dto.RegisterRequest;
import com.dcpost.customer.auth.service.AuthService;
import com.dcpost.customer.auth.service.RegistrationService;
import com.dcpost.customer.user.repository.UserRepository;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = CustomerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class AuthPersistenceIT {
    @Autowired AuthService auth;
    @Autowired RegistrationService registration;
    @Autowired UserRepository users;
    @Autowired PlatformTransactionManager transactions;
    private String email;
    private long userId;

    @BeforeEach
    void register() {
        email = "auth-" + UUID.randomUUID() + "@example.invalid";
        String phone = "09" + String.format("%08d", Math.floorMod(System.nanoTime(), 100_000_000));
        userId = registration.register(new RegisterRequest("Auth persistence", email, phone, "safe-password")).id();
    }

    @AfterEach
    void cleanUp() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> users.deleteById(userId));
    }

    /** Phiên được commit trong MySQL; login mới vô hiệu access và refresh cũ. */
    @Test
    void Test_login_001() {
        var first = auth.login(email, "safe-password");
        var second = auth.login(email, "safe-password");
        assertThrows(BadCredentialsException.class, () -> auth.authenticate(first.accessToken()));
        assertThrows(BadCredentialsException.class, () -> auth.refresh(first.refreshToken()));
        assertEquals(userId, auth.authenticate(second.accessToken()).userId());
        assertEquals(2, users.findById(userId).orElseThrow().getAuthVersion());
    }

    /** Hai request refresh đồng thời chỉ một request được xoay cùng token cũ. */
    @Test
    void Test_refresh_001() throws Exception {
        var session = auth.login(email, "safe-password");
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> refreshAfter(start, session.refreshToken()));
            var second = workers.submit(() -> refreshAfter(start, session.refreshToken()));
            start.countDown();
            int succeeded = first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS);
            assertEquals(1, succeeded);
        }
        assertThrows(BadCredentialsException.class, () -> auth.refresh(session.refreshToken()));
    }

    private int refreshAfter(CountDownLatch start, String token) throws InterruptedException {
        assertTrue(start.await(5, TimeUnit.SECONDS));
        try {
            auth.refresh(token);
            return 1;
        } catch (BadCredentialsException expected) {
            return 0;
        }
    }
}
