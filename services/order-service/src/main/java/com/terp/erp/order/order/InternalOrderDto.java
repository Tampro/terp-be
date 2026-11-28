package com.terp.erp.order.order;

import java.math.BigDecimal;
import java.util.List;

/** The order as delivery-service sees it (internal only): destination with the plain phone, and what to bring. */
public record InternalOrderDto(String id, String orderNo, EOrderStatus status, String storeName, String recipientName,
                               String phone, String postcode, String address1, String address2, BigDecimal lat,
                               BigDecimal lon, String deliveryNote, String note, BigDecimal total, List<Line> lines) {
    public record Line(String name, int quantity) {
    }
}
