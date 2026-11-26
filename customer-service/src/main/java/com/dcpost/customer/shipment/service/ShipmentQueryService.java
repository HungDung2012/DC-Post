package com.dcpost.customer.shipment.service;

import com.dcpost.customer.integration.common.IntegrationException;
import com.dcpost.customer.integration.shipping.ShippingClient;
import com.dcpost.customer.security.CustomerPrincipal;
import com.dcpost.customer.shipment.dto.*;
import com.dcpost.customer.shipment.dto.ShipmentDetailResponse.*;
import com.dcpost.customer.shipment.model.SyncStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.NEVER)
public class ShipmentQueryService {
    private static final Instant MIN_DATABASE_DATE = Instant.parse("1000-01-01T00:00:00Z");
    private static final Instant MAX_DATABASE_DATE = Instant.parse("9999-12-31T23:59:59.999999Z");
    private final ShipmentReadService reader;
    private final ShippingClient shipping;
    public ShipmentQueryService(ShipmentReadService reader, ShippingClient shipping) {
        this.reader = reader; this.shipping = shipping;
    }

    public ShipmentListResponse list(CustomerPrincipal principal, ShipmentListQuery query) {
        if (query.shippingStatus() != null || query.paymentStatus() != null)
            throw new ShipmentQueryException(ShipmentQueryException.Code.FILTER_UNAVAILABLE);
        if (query.page() < 0 || query.page() > 1_000_000 || query.size() < 1 || query.size() > 100
                || (query.q() != null && query.q().length() > 500) || query.sort() != null
                || outsideDatabaseRange(query.createdFrom()) || outsideDatabaseRange(query.createdBefore())
                || (query.createdFrom() != null && query.createdBefore() != null
                    && !query.createdFrom().isBefore(query.createdBefore())))
            throw new ShipmentQueryException(ShipmentQueryException.Code.INVALID_SHIPMENT_QUERY);
        return reader.list(principal.userId(), query);
    }

    private static boolean outsideDatabaseRange(Instant value) {
        return value != null && (value.isBefore(MIN_DATABASE_DATE) || value.isAfter(MAX_DATABASE_DATE));
    }

    public ShipmentDetailResponse detail(CustomerPrincipal principal, long id) {
        var local = reader.detail(principal.userId(), id);
        if (local.syncStatus() != SyncStatus.SYNCED)
            return new ShipmentDetailResponse(local, new Tracking(Availability.NOT_READY, null, null, List.of(), null));
        try {
            var tracking = shipping.getTracking(local.trackingCode());
            if (tracking == null || !local.trackingCode().equals(tracking.trackingCode())
                    || tracking.sourceStatus() == null || tracking.sourceStatus().isBlank()
                    || tracking.updatedAt() == null || tracking.publicEvents() == null
                    || tracking.publicEvents().stream().anyMatch(event -> event == null
                        || event.sourceStatus() == null || event.sourceStatus().isBlank() || event.occurredAt() == null))
                throw new IntegrationException(IntegrationException.Kind.INVALID_RESPONSE, false);
            var events = tracking.publicEvents().stream()
                    .map(event -> new TrackingEvent(event.sourceStatus(), event.occurredAt(), event.area())).toList();
            return new ShipmentDetailResponse(local, new Tracking(Availability.AVAILABLE, tracking.sourceStatus(),
                    tracking.updatedAt(), events, null));
        } catch (IntegrationException exception) {
            return new ShipmentDetailResponse(local, new Tracking(Availability.UNAVAILABLE, null, null, List.of(),
                    "EXTERNAL_" + exception.kind().name()));
        }
    }
}
