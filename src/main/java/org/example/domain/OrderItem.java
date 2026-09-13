package org.example.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private CustomerOrder order;

    private Long productColorId;
    private Long sizeSetId;
    private String productCode;
    private String productName;
    private String colorName;
    private String sizeSetName;
    private String sizeLabels;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private BigDecimal priceEach;

    @Column(nullable = false)
    private boolean outOfStock;

    @Column(length = 1000)
    private String outOfStockReason;

    public Long getId() {
        return id;
    }

    public CustomerOrder getOrder() {
        return order;
    }

    public void setOrder(CustomerOrder order) {
        this.order = order;
    }

    public Long getProductColorId() {
        return productColorId;
    }

    public void setProductColorId(Long productColorId) {
        this.productColorId = productColorId;
    }

    public Long getSizeSetId() {
        return sizeSetId;
    }

    public void setSizeSetId(Long sizeSetId) {
        this.sizeSetId = sizeSetId;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getColorName() {
        return colorName;
    }

    public void setColorName(String colorName) {
        this.colorName = colorName;
    }

    public String getSizeSetName() {
        return sizeSetName;
    }

    public void setSizeSetName(String sizeSetName) {
        this.sizeSetName = sizeSetName;
    }

    public String getSizeLabels() {
        return sizeLabels;
    }

    public void setSizeLabels(String sizeLabels) {
        this.sizeLabels = sizeLabels;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getPriceEach() {
        return priceEach;
    }

    public void setPriceEach(BigDecimal priceEach) {
        this.priceEach = priceEach;
    }

    public boolean isOutOfStock() {
        return outOfStock;
    }

    public void setOutOfStock(boolean outOfStock) {
        this.outOfStock = outOfStock;
    }

    public String getOutOfStockReason() {
        return outOfStockReason;
    }

    public void setOutOfStockReason(String outOfStockReason) {
        this.outOfStockReason = outOfStockReason;
    }
}
