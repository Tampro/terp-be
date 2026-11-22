package com.terp.erp.project.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.terp.erp.project.common.EProjectError;
import com.terp.erp.project.config.ProjectProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * crm-service as the caller: crm-service checks the caller's CRM permissions and company itself; its 4xx errors are
 * relayed as they are (RemoteApiException), an outage is PRJ_003.
 */
@Component
public class CrmClient {
    private final CallerHttp http;

    public CrmClient(ProjectProperties props, ObjectMapper mapper) {
        this.http = new CallerHttp(props.crmServiceUrl(), mapper, EProjectError.PRJ_003, "crm-service");
    }

    /** POST /api/v1/crm/leads (LeadForm); answers data.lead.id. */
    public String createLead(Map<String, Object> body) {
        return post("/api/v1/crm/leads", body).path("data").path("lead").path("id").asText(null);
    }

    /** POST /api/v1/crm/deals (DealForm); answers data.deal.id. */
    public String createDeal(Map<String, Object> body) {
        return post("/api/v1/crm/deals", body).path("data").path("deal").path("id").asText(null);
    }

    /** POST /api/v1/crm/contracts (ContractForm); answers data.contract.id. */
    public String createContract(Map<String, Object> body) {
        return post("/api/v1/crm/contracts", body).path("data").path("contract").path("id").asText(null);
    }

    private JsonNode post(String path, Map<String, Object> body) {
        return http.call(c -> c.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body));
    }
}
