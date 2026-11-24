package com.dcpost.customer.shipment;

import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class ShipmentSchemaIT {
    private static final String URL = System.getenv().getOrDefault("TEST_DB_URL",
            "jdbc:mysql://localhost:3306/customer_test?connectionTimeZone=UTC");
    private static final String USER = System.getenv().getOrDefault("TEST_DB_USERNAME", "customer_test");
    private static final String PASSWORD = System.getenv().getOrDefault("TEST_DB_PASSWORD", "customer-test-local-only");
    private Connection connection;

    @BeforeAll
    static void migrate() { Flyway.configure().dataSource(URL, USER, PASSWORD).load().migrate(); }

    @BeforeEach
    void connect() throws Exception {
        connection = DriverManager.getConnection(URL, USER, PASSWORD);
        connection.setAutoCommit(false);
    }

    @AfterEach
    void rollback() throws Exception {
        if (connection != null) { try { connection.rollback(); } finally { connection.close(); } }
    }

    /** Exactly the two planned shipment tables are introduced, without technical queue tables. */
    @Test
    void Test_migrate_001() throws Exception {
        var tables = new HashSet<String>();
        try (var result = connection.getMetaData().getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
            while (result.next()) tables.add(result.getString("TABLE_NAME"));
        }
        tables.remove("flyway_schema_history");
        assertThat(tables).containsExactlyInAnyOrder("users", "roles", "user_roles", "shipments", "shipment_items");
    }

    private void requireSchema() throws Exception {
        try (var result = connection.getMetaData().getTables(connection.getCatalog(), null, "shipments", null)) {
            assertThat(result.next()).as("shipment migration exists").isTrue();
        }
    }

    private long insertShipment(Map<String, Object> overrides) throws Exception {
        var values = new LinkedHashMap<String, Object>();
        values.put("tracking_code", "DC" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT));
        values.put("sender_name", "Sender"); values.put("sender_phone", "0900000000"); values.put("sender_address", "Origin");
        values.put("recipient_name", "Recipient"); values.put("recipient_phone", "0911111111"); values.put("recipient_address", "Destination");
        values.put("weight", 1); values.put("length", 10); values.put("width", 10); values.put("height", 10);
        values.put("shipping_fee", 25000); values.put("pickup_method", "DROP_OFF"); values.put("creation_channel", "COUNTER");
        values.put("drop_off_warehouse_id", 1); values.put("created_by", 1); values.put("created_by_source", "counter");
        values.put("payment_method", "CASH"); values.put("creation_request_scope", "test:counter");
        values.put("creation_request_key", UUID.randomUUID().toString()); values.put("creation_request_hash", "0".repeat(64));
        values.putAll(overrides);
        String sql = "INSERT INTO shipments (" + String.join(",", values.keySet()) + ") VALUES ("
                + String.join(",", Collections.nCopies(values.size(), "?")) + ")";
        try (var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int index = 1;
            for (var value : values.values()) statement.setObject(index++, value);
            statement.executeUpdate();
            try (var result = statement.getGeneratedKeys()) { result.next(); return result.getLong(1); }
        }
    }

    private void rejected(Map<String, Object> overrides, int code) {
        assertThatThrownBy(() -> insertShipment(overrides)).isInstanceOfSatisfying(SQLException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(code));
    }

    /** A counter shipment can exist without creating a customer account. */
    @Test
    void Test_insertShipment_001() throws Exception {
        requireSchema();
        long id = insertShipment(Map.of());
        try (var statement = connection.prepareStatement("SELECT customer_id,sync_status,cancellation_status FROM shipments WHERE shipment_id=?")) {
            statement.setLong(1, id);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getObject(1)).isNull();
                assertThat(result.getString(2)).isEqualTo("PENDING");
                assertThat(result.getString(3)).isEqualTo("NONE");
            }
        }
    }

    /** ONLINE requires a local customer; COUNTER cannot request home pickup. */
    @Test
    void Test_insertShipment_002() throws Exception {
        requireSchema();
        rejected(Map.of("creation_channel", "ONLINE"), 3819);
        rejected(Map.of("pickup_method", "HOME_PICKUP"), 3819);
        var missingLocation = new HashMap<String, Object>(); missingLocation.put("drop_off_warehouse_id", null);
        rejected(missingLocation, 3819);
    }

    /** MySQL enforces positive dimensions and nonnegative integral VND fees. */
    @Test
    void Test_insertShipment_003() throws Exception {
        requireSchema();
        for (String field : List.of("weight", "length", "width", "height")) {
            rejected(Map.of(field, 0), 3819);
            rejected(Map.of(field, -1), 3819);
        }
        rejected(Map.of("shipping_fee", -1), 3819);
        rejected(Map.of("shipping_fee", new java.math.BigDecimal("1.50")), 3819);
    }

    /** A deterministic collision is rejected by the database, without a probabilistic uniqueness test. */
    @Test
    void Test_insertShipment_004() throws Exception {
        requireSchema();
        var duplicate = Map.<String, Object>of("tracking_code", "DC" + "A".repeat(32));
        insertShipment(duplicate);
        rejected(duplicate, 1062);
    }

    /** Request keys are scoped and case sensitive; the same scoped key cannot create a second shipment. */
    @Test
    void Test_insertShipment_005() throws Exception {
        requireSchema();
        var duplicate = Map.<String, Object>of("creation_request_key", "Key-A");
        insertShipment(duplicate);
        rejected(duplicate, 1062);
        assertThat(insertShipment(Map.of("creation_request_key", "Key-A", "creation_request_scope", "test:other"))).isPositive();
        assertThat(insertShipment(Map.of("creation_request_key", "key-a"))).isPositive();
    }

    /** Local customer references are real foreign keys; warehouse and actor IDs are logical INT references. */
    @Test
    void Test_migrate_002() throws Exception {
        requireSchema();
        var references = new HashSet<String>();
        try (var result = connection.getMetaData().getImportedKeys(connection.getCatalog(), null, "shipments")) {
            while (result.next()) {
                assertThat(result.getString("PKTABLE_CAT")).isEqualTo(connection.getCatalog());
                references.add(result.getString("PKTABLE_NAME"));
            }
        }
        assertThat(references).containsExactly("users");
        for (String field : List.of("drop_off_warehouse_id", "created_by", "cancelled_by")) {
            try (var result = connection.getMetaData().getColumns(connection.getCatalog(), null, "shipments", field)) {
                assertThat(result.next()).isTrue(); assertThat(result.getInt("DATA_TYPE")).isEqualTo(Types.INTEGER);
            }
        }
        rejected(Map.of("customer_id", Long.MAX_VALUE), 1452);
    }

    /** Items enforce positive quantity, nonnegative declared value and a local shipment foreign key. */
    @Test
    void Test_insertItem_001() throws Exception {
        requireSchema();
        long id = insertShipment(Map.of());
        assertThat(insertItem(id, 1, "0")).isEqualTo(1);
        assertThatThrownBy(() -> insertItem(id, 0, "0")).isInstanceOfSatisfying(SQLException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(3819));
        assertThatThrownBy(() -> insertItem(id, 1, "-1")).isInstanceOfSatisfying(SQLException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(3819));
        assertThatThrownBy(() -> insertItem(Long.MAX_VALUE, 1, "0")).isInstanceOfSatisfying(SQLException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(1452));
    }

    private int insertItem(long shipmentId, int quantity, String value) throws Exception {
        try (var statement = connection.prepareStatement("INSERT INTO shipment_items (shipment_id,item_name,item_category,quantity,declared_value) VALUES (?,'Book','Books',?,?)")) {
            statement.setLong(1, shipmentId); statement.setInt(2, quantity);
            statement.setBigDecimal(3, new java.math.BigDecimal(value)); return statement.executeUpdate();
        }
    }
}
