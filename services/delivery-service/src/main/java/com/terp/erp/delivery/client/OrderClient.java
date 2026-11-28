package com.terp.erp.delivery.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.terp.erp.delivery.common.EDeliveryError;
import com.terp.erp.delivery.config.DeliveryProperties;
import com.terp.erp.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** order-service internal read of an order snapshot (plain phone: only the rider view shows it). */
@Component
@RequiredArgsConstructor
public class OrderClient {
    private final InternalHttp http;
    private final DeliveryProperties props;

    public record Line(String name, int quantity) {
    }

    public record OrderView(String orderNo, String status, String storeName, String recipientName, String phone,
                            String postcode, String address1, String address2, BigDecimal lat, BigDecimal lon,
                            String deliveryNote, String note, BigDecimal total, List<Line> lines) {
    }

    public OrderView order(long companyId, long orderId) {
        String url = UriComponentsBuilder.fromUriString(props.orderServiceUrl() + "/api/v1/internal/order/orders/" + orderId)
                .queryParam("companyId", companyId).toUriString();
        JsonNode d;
        try {
            d = http.get(url, EDeliveryError.DLV_001);
        } catch (ApiException e) {
            if (e.getErrorCode() == EDeliveryError.DLV_001) {
                // The delivery exists, so its order must: a missing order is an inconsistency, not the caller's 404.
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, EDeliveryError.DLV_003);
            }
            throw e;
        }
        List<Line> lines = new ArrayList<>();
        d.path("lines").forEach(l -> lines.add(new Line(l.path("name").asText(""), l.path("quantity").asInt())));
        return new OrderView(text(d, "orderNo"), text(d, "status"), text(d, "storeName"), text(d, "recipientName"),
                text(d, "phone"), text(d, "postcode"), text(d, "address1"), text(d, "address2"), dec(d, "lat"),
                dec(d, "lon"), text(d, "deliveryNote"), text(d, "note"), dec(d, "total"), lines);
    }

    private static String text(JsonNode d, String f) {
        return d.hasNonNull(f) ? d.get(f).asText() : null;
    }

    private static BigDecimal dec(JsonNode d, String f) {
        return d.hasNonNull(f) ? new BigDecimal(d.get(f).asText()) : null;
    }
}
