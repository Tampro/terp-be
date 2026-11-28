package com.terp.erp.delivery.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.terp.erp.delivery.common.EDeliveryError;
import com.terp.erp.delivery.config.DeliveryProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** IAM internal rider check: is this user an active member of the company holding DELIVERY_RIDE? 404 → DLV_004. */
@Component
@RequiredArgsConstructor
public class IamClient {
    private final InternalHttp http;
    private final DeliveryProperties props;

    public record Rider(String userId, String keycloakSub, String fullName, boolean active, boolean canRide) {
    }

    public Rider rider(long companyId, long userId) {
        JsonNode d = http.get(props.iamServiceUrl() + "/api/v1/internal/companies/" + companyId + "/riders/" + userId,
                EDeliveryError.DLV_004);
        return new Rider(d.path("userId").asText(), d.path("keycloakSub").asText(null), d.path("fullName").asText(null),
                d.path("active").asBoolean(false), d.path("canRide").asBoolean(false));
    }
}
