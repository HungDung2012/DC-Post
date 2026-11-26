package com.dcpost.customer.shipment.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("customer.shipment.sync")
public record ShipmentSyncProperties(@DefaultValue("5") int maxAttempts,
        @DefaultValue("2m") Duration processingTimeout, @DefaultValue("30s") Duration retryInitialDelay,
        @DefaultValue("15m") Duration retryMaxDelay, @DefaultValue("1m") Duration reconciliationDelay) {
    public ShipmentSyncProperties {
        if (maxAttempts < 1 || maxAttempts > 100) throw new IllegalArgumentException("Sync attempt limit must be between 1 and 100");
        for (var duration : new Duration[] {processingTimeout, retryInitialDelay, retryMaxDelay, reconciliationDelay})
            if (duration == null || duration.compareTo(Duration.ofSeconds(1)) < 0 || duration.compareTo(Duration.ofDays(1)) > 0)
                throw new IllegalArgumentException("Sync durations must be between one second and one day");
        if (retryInitialDelay.compareTo(retryMaxDelay) > 0)
            throw new IllegalArgumentException("Initial sync retry delay cannot exceed its maximum");
    }

    public Duration retryDelay(int attempt) {
        var delay = retryInitialDelay;
        for (int index = 1; index < attempt && delay.compareTo(retryMaxDelay) < 0; index++) {
            delay = delay.multipliedBy(2);
            if (delay.compareTo(retryMaxDelay) > 0) return retryMaxDelay;
        }
        return delay;
    }
}
