package com.dcpost.customer.shipment;

import com.dcpost.customer.shipment.config.ShipmentSyncProperties;
import com.dcpost.customer.integration.config.IntegrationProperties;
import com.dcpost.customer.shipment.service.ShipmentSyncStateService;
import com.dcpost.customer.shipment.repository.ShipmentRepository;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ShipmentSyncPropertiesTest {
    /** Retry delays grow exponentially and remain capped even for a large persisted attempt count. */
    @Test void Test_retryDelay_001() {
        var policy = new ShipmentSyncProperties(5, Duration.ofMinutes(2), Duration.ofSeconds(5), Duration.ofSeconds(20), Duration.ofMinutes(1));
        assertThat(policy.retryDelay(1)).isEqualTo(Duration.ofSeconds(5));
        assertThat(policy.retryDelay(2)).isEqualTo(Duration.ofSeconds(10));
        assertThat(policy.retryDelay(3)).isEqualTo(Duration.ofSeconds(20));
        assertThat(policy.retryDelay(Integer.MAX_VALUE)).isEqualTo(Duration.ofSeconds(20));
    }

    /** Invalid limits and timing cannot create tight retry loops or overflow backoff calculations. */
    @Test void Test_constructor_001() {
        for (int limit : new int[] {0, -1, 101})
            assertThatThrownBy(() -> new ShipmentSyncProperties(limit, Duration.ofMinutes(2), Duration.ofSeconds(5), Duration.ofSeconds(20), Duration.ofMinutes(1)))
                    .isInstanceOf(IllegalArgumentException.class);
        for (var duration : new Duration[] {null, Duration.ZERO, Duration.ofNanos(1), Duration.ofSeconds(-1), Duration.ofDays(2)}) {
            assertThatThrownBy(() -> new ShipmentSyncProperties(5, duration, Duration.ofSeconds(5), Duration.ofSeconds(20), Duration.ofMinutes(1)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new ShipmentSyncProperties(5, Duration.ofMinutes(2), duration, Duration.ofSeconds(20), Duration.ofMinutes(1)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new ShipmentSyncProperties(5, Duration.ofMinutes(2), Duration.ofSeconds(5), duration, Duration.ofMinutes(1)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new ShipmentSyncProperties(5, Duration.ofMinutes(2), Duration.ofSeconds(5), Duration.ofSeconds(20), duration))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new ShipmentSyncProperties(5, Duration.ofMinutes(2), Duration.ofSeconds(30), Duration.ofSeconds(20), Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** A processing lease cannot expire during the configured connect plus read timeout window. */
    @Test void Test_processingTimeout_001() {
        var integration = new IntegrationProperties("http://localhost:8091", "http://localhost:8091", "http://localhost:8091",
                Duration.ofSeconds(3), Duration.ofSeconds(10));
        for (int seconds : new int[] {5, 13}) {
            var policy = new ShipmentSyncProperties(5, Duration.ofSeconds(seconds), Duration.ofSeconds(5), Duration.ofSeconds(20), Duration.ofMinutes(1));
            assertThatThrownBy(() -> new ShipmentSyncStateService(mock(ShipmentRepository.class), policy, integration))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
