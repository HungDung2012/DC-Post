package com.dcpost.customer.shipment.repository;

import com.dcpost.customer.shipment.entity.Shipment;
import java.util.Optional;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShipmentRepository extends JpaRepository<Shipment, Long>, JpaSpecificationExecutor<Shipment> {
    Optional<Shipment> findByCreationRequestScopeAndCreationRequestKey(String scope, String key);
    @EntityGraph(attributePaths = "items")
    Optional<Shipment> findByIdAndCustomerId(long id, long customerId);

    // Until verified local payments exist, VNPAY remains ineligible even if its sync state is changed.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE shipments
        SET sync_status='PROCESSING', sync_started_at=:now,
            sync_attempt_count=sync_attempt_count+1, sync_last_error_code=NULL
        WHERE shipment_id=:id AND sync_status='PENDING' AND payment_method='CASH'
          AND cancellation_status='NONE' AND cancelled_at IS NULL
          AND sync_attempt_count < 2147483647
          AND (sync_next_attempt_at IS NULL OR sync_next_attempt_at <= :now)
        """, nativeQuery = true)
    int claimCashSync(@Param("id") long id, @Param("now") Instant now);

    @Query(value = """
        SELECT shipment_id FROM shipments
        WHERE sync_status='PENDING' AND payment_method='CASH'
          AND cancellation_status='NONE' AND cancelled_at IS NULL
          AND sync_attempt_count < 2147483647
          AND (sync_next_attempt_at IS NULL OR sync_next_attempt_at <= :now)
        ORDER BY sync_next_attempt_at, shipment_id
        LIMIT :limit
        """, nativeQuery = true)
    List<Long> findSyncCandidates(@Param("now") Instant now, @Param("limit") int limit);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE shipments
        SET sync_status=:status, sync_last_error_code=:errorCode, sync_next_attempt_at=NULL
        WHERE shipment_id=:id AND sync_status='PROCESSING'
          AND sync_attempt_count=:attempt AND sync_started_at=:startedAt
        """, nativeQuery = true)
    int finishSync(@Param("id") long id, @Param("attempt") int attempt, @Param("startedAt") Instant startedAt,
            @Param("status") String status, @Param("errorCode") String errorCode);
}
