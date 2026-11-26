package com.dcpost.customer.shipment.repository;

import com.dcpost.customer.shipment.entity.Shipment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;

public interface ShipmentRepository extends JpaRepository<Shipment, Long>, JpaSpecificationExecutor<Shipment> {
    Optional<Shipment> findByCreationRequestScopeAndCreationRequestKey(String scope, String key);
    @EntityGraph(attributePaths = "items")
    Optional<Shipment> findByIdAndCustomerId(long id, long customerId);
}
