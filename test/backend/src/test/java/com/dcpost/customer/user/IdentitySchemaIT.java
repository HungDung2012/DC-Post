package com.dcpost.customer.user;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.HashSet;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdentitySchemaIT {
    private static final String URL = "jdbc:mysql://localhost:3307/customer_test?connectionTimeZone=UTC";
    private static final String USER = "customer_test";
    private static final String PASSWORD = "customer-test-local-only";

    @BeforeAll
    static void migrate() {
        Flyway.configure().dataSource(URL, USER, PASSWORD)
                .locations("classpath:db/migration").load().migrate();
    }

    /** Migration tạo đủ ba bảng danh tính trong database kiểm thử riêng. */
    @Test
    void Test_migrate_001() throws Exception {
        Set<String> tables = new HashSet<>();
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             ResultSet result = connection.getMetaData().getTables("customer_test", null, "%", new String[]{"TABLE"})) {
            while (result.next()) {
                tables.add(result.getString("TABLE_NAME"));
            }
        }
        assertTrue(tables.containsAll(Set.of("users", "roles", "user_roles")),
                "Identity migration must create users, roles and user_roles; found: " + tables);
    }

    /** Email và số điện thoại có unique constraint ở database để chống request đồng thời. */
    @Test
    void Test_migrate_002() throws Exception {
        Set<String> uniqueColumns = new HashSet<>();
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             ResultSet result = connection.getMetaData().getIndexInfo("customer_test", null, "users", true, false)) {
            while (result.next()) {
                uniqueColumns.add(result.getString("COLUMN_NAME"));
            }
        }
        assertTrue(uniqueColumns.containsAll(Set.of("email", "phone")),
                "Email and phone must have database uniqueness constraints");
    }

    /** Bảng liên kết role sử dụng đúng composite primary key. */
    @Test
    void Test_migrate_003() throws Exception {
        Set<String> primaryKey = new HashSet<>();
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             ResultSet result = connection.getMetaData().getPrimaryKeys("customer_test", null, "user_roles")) {
            while (result.next()) {
                primaryKey.add(result.getString("COLUMN_NAME"));
            }
        }
        assertEquals(Set.of("user_id", "role_id"), primaryKey);
    }

    /** Chạy Flyway lại không nhân đôi role và không seed role đặc quyền. */
    @Test
    void Test_migrate_004() throws Exception {
        migrate();
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             var statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT role_name FROM roles")) {
            int count = 0;
            while (result.next()) {
                assertEquals("CUSTOMER", result.getString(1));
                count++;
            }
            assertEquals(1, count);
        }
    }

    /** Foreign key của bảng liên kết chỉ tham chiếu bảng cùng database Customer. */
    @Test
    void Test_migrate_005() throws Exception {
        Set<String> referencedTables = new HashSet<>();
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             ResultSet result = connection.getMetaData().getImportedKeys("customer_test", null, "user_roles")) {
            while (result.next()) {
                assertEquals("customer_test", result.getString("PKTABLE_CAT"));
                referencedTables.add(result.getString("PKTABLE_NAME"));
            }
        }
        assertEquals(Set.of("users", "roles"), referencedTables);
    }

    /** MySQL thực sự từ chối email và số điện thoại trùng khi ghi dữ liệu. */
    @Test
    void Test_migrate_006() throws Exception {
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD)) {
            connection.setAutoCommit(false);
            try {
                insertUser(connection, "identity-constraint@example.invalid", "0900000001");
                assertThrows(SQLIntegrityConstraintViolationException.class,
                        () -> insertUser(connection, "identity-constraint@example.invalid", "0900000002"));
                assertThrows(SQLIntegrityConstraintViolationException.class,
                        () -> insertUser(connection, "identity-other@example.invalid", "0900000001"));
            } finally {
                connection.rollback();
            }
        }
    }

    private void insertUser(Connection connection, String email, String phone) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO users (full_name, email, phone, password_hash) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, "Identity constraint fixture");
            statement.setString(2, email);
            statement.setString(3, phone);
            statement.setString(4, "fixture-hash-not-an-account-password");
            statement.executeUpdate();
        }
    }
}
