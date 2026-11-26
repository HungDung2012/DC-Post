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
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface ShipmentRepository extends JpaRepository<Shipment, Long>, JpaSpecificationExecutor<Shipment> {
    Optional<Shipment> findByCreationRequestScopeAndCreationRequestKey(String scope, String key);
    @EntityGraph(attributePaths = "items")
    Optional<Shipment> findByIdAndCustomerId(long id, long customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select shipment from Shipment shipment where shipment.id=:id")
    Optional<Shipment> findLockedById(@Param("id") long id);

    // Until verified local payments exist, VNPAY remains ineligible even if its sync state is changed.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE shipments
        SET sync_status='PROCESSING', sync_started_at=:now,
            sync_attempt_count=sync_attempt_count+1, sync_last_error_code=NULL
        WHERE shipment_id=:id AND sync_status='PENDING' AND payment_method='CASH'
          AND cancellation_status='NONE' AND cancelled_at IS NULL
          AND sync_attempt_count < :maxAttempts
          AND (sync_next_attempt_at IS NULL OR sync_next_attempt_at <= :now)
        """, nativeQuery = true)
    int claimCashSync(@Param("id") long id, @Param("now") Instant now, @Param("maxAttempts") int maxAttempts);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE shipments SET sync_status='BLOCKED', sync_last_error_code='SYNC_ATTEMPTS_EXHAUSTED', sync_next_attempt_at=NULL
        WHERE shipment_id=:id AND sync_status='PENDING' AND payment_method='CASH'
          AND cancellation_status='NONE' AND cancelled_at IS NULL AND sync_attempt_count >= :maxAttempts
          AND (sync_next_attempt_at IS NULL OR sync_next_attempt_at <= :now)
        """, nativeQuery = true)
    int blockExhaustedCashSync(@Param("id") long id, @Param("now") Instant now, @Param("maxAttempts") int maxAttempts);

    @Query(value = """
        SELECT shipment_id FROM shipments
        WHERE sync_status='PENDING' AND payment_method='CASH'
          AND cancellation_status='NONE' AND cancelled_at IS NULL
          AND (sync_next_attempt_at IS NULL OR sync_next_attempt_at <= :now)
        ORDER BY sync_next_attempt_at, shipment_id
        LIMIT :limit
        """, nativeQuery = true)
    List<Long> findSyncCandidates(@Param("now") Instant now, @Param("limit") int limit);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE shipments
        SET sync_status=:status, sync_last_error_code=:errorCode, sync_next_attempt_at=:nextAttempt
        WHERE shipment_id=:id AND sync_status='PROCESSING'
          AND sync_attempt_count=:attempt AND sync_started_at=:startedAt
        """, nativeQuery = true)
    int finishSync(@Param("id") long id, @Param("attempt") int attempt, @Param("startedAt") Instant startedAt,
            @Param("status") String status, @Param("errorCode") String errorCode, @Param("nextAttempt") Instant nextAttempt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE shipments SET sync_status='UNKNOWN', sync_last_error_code='SYNC_PROCESSING_INTERRUPTED', sync_next_attempt_at=:now
        WHERE shipment_id=:id AND sync_status='PROCESSING'
          AND (sync_started_at IS NULL OR sync_started_at <= :cutoff)
        """, nativeQuery = true)
    int expireProcessingSync(@Param("id") long id, @Param("cutoff") Instant cutoff, @Param("now") Instant now);

    @Query(value = """
        SELECT shipment_id FROM shipments
        WHERE (sync_status='PROCESSING' AND (sync_started_at IS NULL OR sync_started_at <= :cutoff))
           OR (sync_status='UNKNOWN' AND (sync_next_attempt_at IS NULL OR sync_next_attempt_at <= :now))
        ORDER BY sync_next_attempt_at, shipment_id LIMIT :limit
        """, nativeQuery = true)
    List<Long> findSyncRecoveryCandidates(@Param("now") Instant now, @Param("cutoff") Instant cutoff, @Param("limit") int limit);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE shipments SET sync_started_at=:now, sync_next_attempt_at=:leaseUntil
        WHERE shipment_id=:id AND sync_status='UNKNOWN'
          AND (sync_next_attempt_at IS NULL OR sync_next_attempt_at <= :now)
        """, nativeQuery = true)
    int claimSyncRecovery(@Param("id") long id, @Param("now") Instant now, @Param("leaseUntil") Instant leaseUntil);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE shipments SET sync_status=:status, sync_last_error_code=:errorCode, sync_next_attempt_at=:nextAttempt
        WHERE shipment_id=:id AND sync_status='UNKNOWN'
          AND sync_attempt_count=:attempt AND sync_started_at=:startedAt
        """, nativeQuery = true)
    int finishSyncRecovery(@Param("id") long id, @Param("attempt") int attempt, @Param("startedAt") Instant startedAt,
            @Param("status") String status, @Param("errorCode") String errorCode, @Param("nextAttempt") Instant nextAttempt);
}
