package com.dcpost.customer.shipment.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.dcpost.customer.shipment.service.*;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "customer.shipment.sync", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ShipmentSchedulingConfiguration {
    @Bean
    ShipmentSyncScheduler shipmentSyncScheduler(ShipmentSyncStateService state, ShipmentSyncService sync, Clock clock,
            @Value("${customer.shipment.sync.batch-size:10}") int batchSize) {
        return new ShipmentSyncScheduler(state, sync, clock, batchSize);
    }
}
