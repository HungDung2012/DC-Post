package com.dcpost.customer.shipment.service;

import com.dcpost.customer.integration.pricing.PricingClient;
import com.dcpost.customer.integration.pricing.dto.FeeRequest;
import com.dcpost.customer.integration.common.IntegrationException;
import com.dcpost.customer.shipment.model.PickupMethod;
import com.dcpost.customer.shipment.entity.Shipment;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.dcpost.customer.integration.warehouse.WarehouseClient;
import com.dcpost.customer.security.CustomerPrincipal;
import com.dcpost.customer.security.InternalApiKeyFilter.ServicePrincipal;
import com.dcpost.customer.shipment.model.CreationChannel;
import com.dcpost.customer.shipment.dto.*;
import com.dcpost.customer.shipment.repository.ShipmentRepository;
import jakarta.validation.Validator;
import jakarta.validation.ConstraintViolationException;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.HexFormat;

@Service
public class ShipmentCreationService {
    private final ShipmentWriteService writer;
    private final PricingClient pricing;
    private final WarehouseClient warehouses;
    private final Validator validator;
    private final ShipmentRepository shipments;
    public ShipmentCreationService(ShipmentRepository shipments, ShipmentWriteService writer,
            PricingClient pricing, WarehouseClient warehouses, Validator validator) {
        this.writer = writer; this.pricing = pricing; this.warehouses = warehouses; this.validator = validator;
        this.shipments = shipments;
    }

    // Fail fast if a caller tries to hold a database transaction across external HTTP calls.
    @Transactional(propagation = Propagation.NEVER)
    public ShipmentCreated create(CustomerPrincipal principal, String requestKey, CreateShipmentRequest request) {
        if (principal == null || principal.userId() <= 0) throw invalid();
        int actorId;
        try { actorId = Math.toIntExact(principal.userId()); }
        catch (ArithmeticException exception) { throw invalid(); }
        return create(principal.userId(), actorId, "customer", CreationChannel.ONLINE,
                "customer:" + principal.userId(), requestKey, request, request);
    }

    @Transactional(propagation = Propagation.NEVER)
    public ShipmentCreated createCounter(ServicePrincipal principal, String requestKey, CounterShipmentRequest request) {
        if (principal == null || principal.serviceName() == null
                || !principal.serviceName().matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,63}") || request == null) throw invalid();
        var violations = validator.validate(request);
        if (!violations.isEmpty()) throw new ConstraintViolationException(violations);
        if (request.shipment().pickupMethod() != PickupMethod.DROP_OFF) throw invalid();
        return create(null, request.createdBy(), principal.serviceName(), CreationChannel.COUNTER,
                "service:" + principal.serviceName(), requestKey, request.shipment(), request);
    }

    private ShipmentCreated create(Long customerId, int actorId, String actorSource, CreationChannel channel,
            String scope, String requestKey, CreateShipmentRequest request, Object hashPayload) {
        if (request == null || requestKey == null || !requestKey.matches("[!-~]{1,128}")) throw invalid();
        var violations = validator.validate(request);
        if (!violations.isEmpty()) throw new ConstraintViolationException(violations);
        if ((request.pickupMethod() == PickupMethod.DROP_OFF) != (request.dropOffWarehouseId() != null)) throw invalid();
        var hash = hash(hashPayload);
        var existing = shipments.findByCreationRequestScopeAndCreationRequestKey(scope, requestKey);
        if (existing.isPresent()) return replay(existing.get(), customerId, actorId, actorSource, channel, hash);
        if (request.pickupMethod() == PickupMethod.DROP_OFF && warehouses.getDropOffLocations().stream()
                .noneMatch(location -> Integer.toString(request.dropOffWarehouseId()).equals(location.externalId()))) throw invalid();
        var parcel = request.parcel();
        var quote = pricing.quote(new FeeRequest(request.sender().address(), request.recipient().address(),
                parcel.weightKg(), parcel.lengthCm(), parcel.widthCm(), parcel.heightCm()));
        if (quote == null || quote.amount() == null || !"VND".equals(quote.currency())
                || quote.amount().signum() < 0 || quote.amount().stripTrailingZeros().scale() > 0
                || quote.amount().compareTo(new java.math.BigDecimal("9999999999999")) > 0)
            throw new IntegrationException(IntegrationException.Kind.INVALID_RESPONSE, false);
        if (quote.amount().compareTo(request.confirmedFee()) != 0)
            throw new ShipmentCreationException(ShipmentCreationException.Code.FEE_CHANGED, quote.amount());
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return channel == CreationChannel.ONLINE
                        ? writer.create(customerId, actorId, scope, requestKey, hash, request, quote.amount())
                        : writer.createCounter(actorId, actorSource, scope, requestKey, hash, request, quote.amount());
            } catch (DataIntegrityViolationException exception) {
                // The proxied writer transaction has already rolled back before this recovery read.
                if (duplicate(exception, "uq_shipments_creation_request")) {
                    var winner = shipments.findByCreationRequestScopeAndCreationRequestKey(scope, requestKey);
                    if (winner.isPresent()) return replay(winner.get(), customerId, actorId, actorSource, channel, hash);
                    throw exception;
                }
                if (!duplicate(exception, "uq_shipments_tracking")) throw exception;
            }
        }
        throw new ShipmentCreationException(ShipmentCreationException.Code.TRACKING_UNAVAILABLE);
    }

    private ShipmentCreated replay(Shipment shipment, Long customerId, int actorId, String actorSource,
            CreationChannel channel, String hash) {
        if (!java.util.Objects.equals(customerId, shipment.getCustomerId()) || shipment.getCreatedBy() != actorId
                || !actorSource.equals(shipment.getCreatedBySource()) || channel != shipment.getCreationChannel()
                || !hash.equals(shipment.getCreationRequestHash()))
            throw new ShipmentCreationException(ShipmentCreationException.Code.IDEMPOTENCY_CONFLICT);
        return ShipmentCreated.from(shipment);
    }

    private String hash(Object request) {
        try {
            // A dedicated mapper keeps canonical serialization independent of HTTP mapper configuration.
            var bytes = JsonMapper.builder().build().writeValueAsBytes(request);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Cannot encode shipment request identity", exception);
        }
    }

    private boolean duplicate(DataIntegrityViolationException exception, String constraint) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getErrorCode() == 1062 && sql.getMessage() != null
                    && java.util.regex.Pattern.compile("for key ['`](?:[^'`]+\\.)?" + constraint + "['`]")
                            .matcher(sql.getMessage()).find()) return true;
        }
        return false;
    }

    private ShipmentCreationException invalid() {
        return new ShipmentCreationException(ShipmentCreationException.Code.INVALID_SHIPMENT);
    }
}
