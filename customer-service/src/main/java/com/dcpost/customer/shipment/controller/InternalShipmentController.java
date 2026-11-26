package com.dcpost.customer.shipment.controller;

import com.dcpost.customer.security.InternalApiKeyFilter.ServicePrincipal;
import com.dcpost.customer.shipment.dto.*;
import com.dcpost.customer.shipment.service.ShipmentCreationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/internal/shipments")
public class InternalShipmentController {
    private final ShipmentCreationService creation;

    public InternalShipmentController(ShipmentCreationService creation) { this.creation = creation; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShipmentCreated create(@AuthenticationPrincipal ServicePrincipal principal,
            @RequestHeader("Idempotency-Key") String requestKey, @Valid @RequestBody CounterShipmentRequest request) {
        return creation.createCounter(principal, requestKey, request);
    }
}
