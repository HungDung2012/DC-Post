package com.dcpost.customer.shipment.service;

import com.dcpost.customer.shipment.dto.*;
import com.dcpost.customer.shipment.entity.Shipment;
import com.dcpost.customer.shipment.repository.ShipmentRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShipmentReadService {
    private final ShipmentRepository shipments;
    public ShipmentReadService(ShipmentRepository shipments) { this.shipments = shipments; }

    @Transactional(readOnly = true)
    public ShipmentListResponse list(long customerId, ShipmentListQuery query) {
        Specification<Shipment> owned = (root, criteria, builder) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(builder.equal(root.get("customerId"), customerId));
            if (query.q() != null && !query.q().isBlank()) {
                var needle = "%" + query.q().toLowerCase(Locale.ROOT).replace("!", "!!")
                        .replace("%", "!%").replace("_", "!_") + "%";
                predicates.add(builder.or(builder.like(builder.lower(root.get("trackingCode")), needle, '!'),
                        builder.like(builder.lower(root.get("recipientName")), needle, '!'),
                        builder.like(root.get("recipientPhone"), needle, '!')));
            }
            if (query.paymentMethod() != null) predicates.add(builder.equal(root.get("paymentMethod"), query.paymentMethod()));
            if (query.syncStatus() != null) predicates.add(builder.equal(root.get("syncStatus"), query.syncStatus()));
            if (query.createdFrom() != null) predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), query.createdFrom()));
            if (query.createdBefore() != null) predicates.add(builder.lessThan(root.get("createdAt"), query.createdBefore()));
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        var page = shipments.findAll(owned, PageRequest.of(query.page(), query.size(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return new ShipmentListResponse(page.getContent().stream().map(ShipmentListResponse.Summary::from).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(),
                new ShipmentListResponse.Capabilities(true, false, false));
    }

    @Transactional(readOnly = true)
    public ShipmentDetailResponse.Snapshot detail(long customerId, long id) {
        return ShipmentDetailResponse.Snapshot.from(shipments.findByIdAndCustomerId(id, customerId)
                .orElseThrow(() -> new ShipmentQueryException(ShipmentQueryException.Code.SHIPMENT_NOT_FOUND)));
    }
}
