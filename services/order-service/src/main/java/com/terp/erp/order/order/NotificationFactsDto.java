package com.terp.erp.order.order;

import java.math.BigDecimal;

/** What notification-service needs for an order email (internal only): the plain address and the facts to show. */
public record NotificationFactsDto(String orderNo, String storeName, String recipientName, String email,
                                   BigDecimal total, EOrderStatus status, String deliveryStatus) {
}
