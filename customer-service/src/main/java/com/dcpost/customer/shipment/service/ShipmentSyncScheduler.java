package com.dcpost.customer.shipment.service;

import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Transactional(propagation = Propagation.NEVER)
public class ShipmentSyncScheduler {
    private static final Logger log = LoggerFactory.getLogger(ShipmentSyncScheduler.class);
    private final ShipmentSyncStateService state;
    private final ShipmentSyncService sync;
    private final Clock clock;
    private final int batchSize;

    public ShipmentSyncScheduler(ShipmentSyncStateService state, ShipmentSyncService sync, Clock clock, int batchSize) {
        if (batchSize < 1 || batchSize > 100) throw new IllegalArgumentException("Shipment sync batch size must be between 1 and 100");
        this.state = state; this.sync = sync; this.clock = clock; this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${customer.shipment.sync.interval-ms:10000}",
            initialDelayString = "${customer.shipment.sync.initial-delay-ms:10000}")
    public void runBatch() {
        List<Long> candidates;
        try {
            candidates = state.candidates(clock.instant(), batchSize);
        } catch (RuntimeException exception) {
            log.warn("Shipment synchronization scan failed ({})", exception.getClass().getSimpleName());
            return;
        }
        for (long shipmentId : candidates) {
            try {
                sync.sync(shipmentId);
            } catch (RuntimeException exception) {
                log.warn("Shipment synchronization interrupted for shipmentId={} ({})", shipmentId, exception.getClass().getSimpleName());
            }
        }
    }
}
