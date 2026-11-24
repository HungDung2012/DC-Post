package com.dcpost.customer.shipment.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "shipment_items")
public class ShipmentItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "shipment_item_id")
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false)
    private Shipment shipment;
    @Column(name = "item_name", nullable = false, length = 255)
    private String name;
    @Column(name = "item_category", nullable = false, length = 100)
    private String category;
    @Column(nullable = false)
    private int quantity;
    @Column(name = "declared_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal declaredValue;
    @Column(length = 500)
    private String note;

    protected ShipmentItem() {}

    ShipmentItem(Shipment shipment, String name, String category, int quantity, BigDecimal declaredValue, String note) {
        this.shipment = shipment; this.name = name; this.category = category;
        this.quantity = quantity; this.declaredValue = declaredValue; this.note = note;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public int getQuantity() { return quantity; }
    public BigDecimal getDeclaredValue() { return declaredValue; }
    public String getNote() { return note; }
}
