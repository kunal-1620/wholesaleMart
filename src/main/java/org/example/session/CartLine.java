package org.example.session;

import java.math.BigDecimal;

public class CartLine {
    private Long productId;
    private Long productColorId;
    private Long sizeSetId;
    private String productCode;
    private String productName;
    private String colorName;
    private String sizeSetName;
    private String sizeLabels;
    private int quantity;
    private BigDecimal priceEach;

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
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

    public BigDecimal lineTotal() {
        return priceEach.multiply(BigDecimal.valueOf(quantity));
    }
}
