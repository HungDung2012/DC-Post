package com.dcpost.customer.shipment.repository;

import com.dcpost.customer.shipment.entity.Shipment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {
    Optional<Shipment> findByCreationRequestScopeAndCreationRequestKey(String scope, String key);
}
