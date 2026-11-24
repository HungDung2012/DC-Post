package com.dcpost.customer.auth;

import com.dcpost.customer.CustomerApplication;
import com.dcpost.customer.auth.dto.RegisterRequest;
import com.dcpost.customer.auth.service.RegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = CustomerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class RegistrationPersistenceIT {
    @Autowired RegistrationService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.dcpost.customer.user.repository.RoleRepository roles;

    /** User và liên kết CUSTOMER được ghi cùng transaction và cùng rollback. */
    @Test
    void Test_register_001() {
        String email = "registration-atomic@example.invalid";
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var account = service.register(new RegisterRequest("Atomic registration", email,
                    "0900000010", "safe-password"));
            assertNotNull(account.id());
            assertEquals(1, jdbc.queryForObject("""
                    SELECT COUNT(*) FROM user_roles ur JOIN roles r ON ur.role_id = r.role_id
                    WHERE ur.user_id = ? AND r.role_name = 'CUSTOMER'
                    """, Integer.class, account.id()));
            status.setRollbackOnly();
        });
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email));
        assertEquals(0, jdbc.queryForObject("""
                SELECT COUNT(*) FROM user_roles ur LEFT JOIN users u ON ur.user_id = u.user_id
                WHERE u.user_id IS NULL
                """, Integer.class));
    }

    /** Lỗi ghi liên kết role rollback user dù caller không mở transaction ngoài. */
    @Test
    void Test_register_002() {
        String email = "registration-rollback@example.invalid";
        var missingRole = org.springframework.beans.BeanUtils.instantiateClass(com.dcpost.customer.user.entity.Role.class);
        org.springframework.test.util.ReflectionTestUtils.setField(missingRole, "id", Integer.MAX_VALUE);
        org.springframework.test.util.ReflectionTestUtils.setField(missingRole, "roleName", "CUSTOMER");
        org.mockito.Mockito.doReturn(java.util.Optional.of(missingRole)).when(roles).findByRoleName("CUSTOMER");
        assertThrows(org.springframework.dao.DataAccessException.class, () -> service.register(
                new RegisterRequest("Rollback registration", email, "0900000011", "safe-password")));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email));
    }
}
