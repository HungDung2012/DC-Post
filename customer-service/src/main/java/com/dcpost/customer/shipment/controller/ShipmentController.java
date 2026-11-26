package com.dcpost.customer.shipment.controller;

import com.dcpost.customer.security.CustomerPrincipal;
import com.dcpost.customer.shipment.dto.*;
import com.dcpost.customer.shipment.model.*;
import com.dcpost.customer.shipment.service.*;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customer/shipments")
public class ShipmentController {
    private final ShipmentCreationService creation;
    private final ShipmentQueryService queries;
    public ShipmentController(ShipmentCreationService creation, ShipmentQueryService queries) {
        this.creation = creation; this.queries = queries;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShipmentCreated create(@AuthenticationPrincipal CustomerPrincipal principal,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody CreateShipmentRequest request) {
        return creation.create(principal, key, request);
    }

    @GetMapping
    public ShipmentListResponse list(@AuthenticationPrincipal CustomerPrincipal principal,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q, @RequestParam(required = false) PaymentMethod paymentMethod,
            @RequestParam(required = false) SyncStatus syncStatus, @RequestParam(required = false) Instant createdFrom,
            @RequestParam(required = false) Instant createdBefore, @RequestParam(required = false) String shippingStatus,
            @RequestParam(required = false) String paymentStatus, @RequestParam(required = false) String sort) {
        return queries.list(principal, new ShipmentListQuery(page, size, q, paymentMethod, syncStatus,
                createdFrom, createdBefore, shippingStatus, paymentStatus, sort));
    }

    @GetMapping("/{id}")
    public ShipmentDetailResponse detail(@AuthenticationPrincipal CustomerPrincipal principal, @PathVariable long id) {
        return queries.detail(principal, id);
    }
}
