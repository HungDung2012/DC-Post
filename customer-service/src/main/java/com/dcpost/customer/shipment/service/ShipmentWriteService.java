package com.dcpost.customer.shipment.service;

import com.dcpost.customer.shipment.dto.*;
import com.dcpost.customer.shipment.entity.Shipment;
import com.dcpost.customer.shipment.model.CreationChannel;
import com.dcpost.customer.shipment.repository.ShipmentRepository;
import java.time.Clock;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShipmentWriteService {
    private final ShipmentRepository shipments;
    private final TrackingCodeGenerator codes;
    private final Clock clock;

    public ShipmentWriteService(ShipmentRepository shipments, TrackingCodeGenerator codes, Clock clock) {
        this.shipments = shipments; this.codes = codes; this.clock = clock;
    }

    @Transactional
    public ShipmentCreated create(long customerId, int actorId, String scope, String key, String hash,
            CreateShipmentRequest request, BigDecimal verifiedFee) {
        var sender = request.sender(); var recipient = request.recipient(); var parcel = request.parcel();
        var shipment = new Shipment(customerId, codes.generate(),
                new Shipment.Contact(sender.name(), sender.phone(), sender.address()),
                new Shipment.Contact(recipient.name(), recipient.phone(), recipient.address()),
                new Shipment.Parcel(parcel.weightKg(), parcel.lengthCm(), parcel.widthCm(), parcel.heightCm()),
                verifiedFee, request.pickupMethod(), CreationChannel.ONLINE, request.paymentMethod(),
                request.dropOffWarehouseId(), new Shipment.Creation(scope, key, hash, "customer", actorId),
                request.note(), clock.instant());
        for (var item : request.items())
            shipment.addItem(item.name(), item.category(), item.quantity(), item.declaredValue(), item.note());
        return ShipmentCreated.from(shipments.saveAndFlush(shipment));
    }
}
