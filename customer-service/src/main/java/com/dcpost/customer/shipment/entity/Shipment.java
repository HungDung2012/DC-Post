package com.dcpost.customer.shipment.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import com.dcpost.customer.shipment.model.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "shipments")
public class Shipment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "shipment_id")
    private Long id;
    @Column(name = "customer_id")
    private Long customerId;
    @Column(name = "tracking_code", nullable = false, length = 50)
    private String trackingCode;
    @Column(name = "sender_name", nullable = false, length = 255)
    private String senderName;
    @Column(name = "sender_phone", nullable = false, length = 20)
    private String senderPhone;
    @Column(name = "sender_address", nullable = false, length = 500)
    private String senderAddress;
    @Column(name = "recipient_name", nullable = false, length = 255)
    private String recipientName;
    @Column(name = "recipient_phone", nullable = false, length = 20)
    private String recipientPhone;
    @Column(name = "recipient_address", nullable = false, length = 500)
    private String recipientAddress;
    @Column(name = "weight", nullable = false, precision = 10, scale = 3)
    private BigDecimal weight;
    @Column(name = "length", nullable = false, precision = 10, scale = 2)
    private BigDecimal length;
    @Column(name = "width", nullable = false, precision = 10, scale = 2)
    private BigDecimal width;
    @Column(name = "height", nullable = false, precision = 10, scale = 2)
    private BigDecimal height;
    @Column(name = "shipping_fee", nullable = false, precision = 15, scale = 2)
    private BigDecimal shippingFee;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "pickup_method", nullable = false, length = 20)
    private PickupMethod pickupMethod;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "creation_channel", nullable = false, length = 20)
    private CreationChannel creationChannel;
    @Column(name = "drop_off_warehouse_id")
    private Integer dropOffWarehouseId;
    @Column(name = "created_by", nullable = false)
    private int createdBy;
    @Column(name = "note", columnDefinition = "TEXT")
    private String note;
    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;
    @Column(name = "cancelled_at")
    private Instant cancelledAt;
    @Column(name = "cancelled_by")
    private Integer cancelledBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;
    @Column(name = "creation_request_scope", nullable = false, length = 160)
    private String creationRequestScope;
    @Column(name = "creation_request_key", nullable = false, length = 128)
    private String creationRequestKey;
    @Column(name = "creation_request_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String creationRequestHash;
    @Column(name = "created_by_source", nullable = false, length = 64)
    private String createdBySource;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "sync_status", nullable = false, length = 30)
    private SyncStatus syncStatus;
    @Column(name = "sync_attempt_count", nullable = false)
    private int syncAttemptCount;
    @Column(name = "sync_next_attempt_at")
    private Instant syncNextAttemptAt;
    @Column(name = "sync_started_at")
    private Instant syncStartedAt;
    @Column(name = "sync_last_error_code", length = 64)
    private String syncLastErrorCode;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "cancellation_status", nullable = false, length = 30)
    private CancellationStatus cancellationStatus;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "cancellation_source", length = 20)
    private CancellationSource cancellationSource;
    @Column(name = "cancellation_request_key", length = 128)
    private String cancellationRequestKey;
    @Column(name = "cancellation_next_check_at")
    private Instant cancellationNextCheckAt;
    @OneToMany(mappedBy = "shipment", cascade = CascadeType.ALL)
    @OrderBy("id ASC")
    private List<ShipmentItem> items = new ArrayList<>();

    protected Shipment() {}

    public record Contact(String name, String phone, String address) {}
    public record Parcel(BigDecimal weight, BigDecimal length, BigDecimal width, BigDecimal height) {}
    public record Creation(String scope, String key, String hash, String actorSource, int actorId) {}

    public Shipment(Long customerId, String trackingCode, Contact sender, Contact recipient, Parcel parcel,
            BigDecimal fee, PickupMethod pickup, CreationChannel channel, PaymentMethod payment,
            Integer warehouseId, Creation creation, String note, Instant now) {
        if (fee == null || fee.signum() < 0 || fee.stripTrailingZeros().scale() > 0)
            throw new IllegalArgumentException("Shipping fee must be a nonnegative whole VND amount");
        this.customerId = customerId;
        this.trackingCode = trackingCode;
        this.senderName = sender.name(); this.senderPhone = sender.phone(); this.senderAddress = sender.address();
        this.recipientName = recipient.name(); this.recipientPhone = recipient.phone(); this.recipientAddress = recipient.address();
        this.weight = parcel.weight(); this.length = parcel.length(); this.width = parcel.width(); this.height = parcel.height();
        this.shippingFee = fee;
        this.pickupMethod = pickup;
        this.creationChannel = channel;
        this.paymentMethod = payment;
        this.dropOffWarehouseId = warehouseId;
        this.createdBy = creation.actorId();
        this.createdBySource = creation.actorSource();
        this.creationRequestScope = creation.scope();
        this.creationRequestKey = creation.key();
        this.creationRequestHash = creation.hash();
        this.note = note;
        this.createdAt = now;
        this.syncStatus = payment == PaymentMethod.VNPAY ? SyncStatus.WAITING_PAYMENT : SyncStatus.PENDING;
        this.syncNextAttemptAt = payment == PaymentMethod.CASH ? now : null;
        this.cancellationStatus = CancellationStatus.NONE;
    }

    public void addItem(String name, String category, int quantity, BigDecimal declaredValue, String note) {
        items.add(new ShipmentItem(this, name, category, quantity, declaredValue, note));
    }

    public Long getId() { return id; }
    public Long getCustomerId() { return customerId; }
    public String getTrackingCode() { return trackingCode; }
    public String getSenderName() { return senderName; }
    public String getSenderPhone() { return senderPhone; }
    public String getSenderAddress() { return senderAddress; }
    public String getRecipientName() { return recipientName; }
    public String getRecipientPhone() { return recipientPhone; }
    public String getRecipientAddress() { return recipientAddress; }
    public BigDecimal getWeight() { return weight; }
    public BigDecimal getLength() { return length; }
    public BigDecimal getWidth() { return width; }
    public BigDecimal getHeight() { return height; }
    public BigDecimal getShippingFee() { return shippingFee; }
    public PickupMethod getPickupMethod() { return pickupMethod; }
    public CreationChannel getCreationChannel() { return creationChannel; }
    public Integer getDropOffWarehouseId() { return dropOffWarehouseId; }
    public int getCreatedBy() { return createdBy; }
    public String getNote() { return note; }
    public String getCancelReason() { return cancelReason; }
    public Instant getCancelledAt() { return cancelledAt; }
    public Integer getCancelledBy() { return cancelledBy; }
    public Instant getCreatedAt() { return createdAt; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public String getCreationRequestScope() { return creationRequestScope; }
    public String getCreationRequestKey() { return creationRequestKey; }
    public String getCreationRequestHash() { return creationRequestHash; }
    public String getCreatedBySource() { return createdBySource; }
    public SyncStatus getSyncStatus() { return syncStatus; }
    public int getSyncAttemptCount() { return syncAttemptCount; }
    public Instant getSyncNextAttemptAt() { return syncNextAttemptAt; }
    public Instant getSyncStartedAt() { return syncStartedAt; }
    public String getSyncLastErrorCode() { return syncLastErrorCode; }
    public CancellationStatus getCancellationStatus() { return cancellationStatus; }
    public CancellationSource getCancellationSource() { return cancellationSource; }
    public String getCancellationRequestKey() { return cancellationRequestKey; }
    public Instant getCancellationNextCheckAt() { return cancellationNextCheckAt; }
    public List<ShipmentItem> getItems() { return List.copyOf(items); }
}
