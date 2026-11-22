package com.terp.erp.notification.delivery;

import com.terp.erp.exception.ApiException;
import com.terp.erp.notification.common.ENotificationError;
import com.terp.erp.notification.delivery.Delivery.EChannel;
import com.terp.erp.notification.delivery.Delivery.EStatus;
import com.terp.erp.security.TenantGuard;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** Delivery log of a company (Settings → Notifications): list, detail, retry of a FAILED delivery. */
@Service
@RequiredArgsConstructor
public class DeliveryService {
    @Schema(description = "One send of one event on one channel. target is masked: email *@domain, else the host.")
    public record DeliveryDto(String id, String companyId, String eventId, String eventType, EChannel channel,
                              String target, EStatus status, int attempts,
                              @Schema(description = "Last failure or skip reason, e.g. SMTP_ERROR, HTTP_500, "
                                      + "URL_REFUSED, EMAIL_DISABLED, NO_RECIPIENT") String lastErrorCode,
                              Instant nextAttemptAt, Instant sentAt, Instant createdDate, Instant updatedDate) {
        static DeliveryDto of(Delivery d) {
            return new DeliveryDto(String.valueOf(d.getId()), String.valueOf(d.getCompanyId()), d.getEventId(),
                    d.getEventType(), d.getChannel(), d.getTarget(), d.getStatus(), d.getAttempts(),
                    d.getLastErrorCode(), d.getNextAttemptAt(), d.getSentAt(), d.getCreatedDate(),
                    d.getUpdatedDate());
        }
    }

    private final DeliveryRepository deliveries;
    private final DeliveryDispatcher dispatcher;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Page<DeliveryDto> list(Long requestedCompanyId, EStatus status, EChannel channel, String eventType,
                                  Instant from, Instant to, Pageable pageable) {
        Long cid = TenantGuard.resolveCompany(requestedCompanyId, ENotificationError.NTF_001);
        String type = eventType == null || eventType.isBlank() ? null : eventType.trim();
        return deliveries.search(cid, status, channel, type, from, to, pageable).map(DeliveryDto::of);
    }

    @Transactional(readOnly = true)
    public DeliveryDto get(Long id) {
        return DeliveryDto.of(require(id));
    }

    /**
     * FAILED → PENDING with a fresh attempt count, then one send right away; the answer is the outcome. Not
     * @Transactional on purpose: the send is a network call (each save commits on its own).
     */
    public DeliveryDto retry(Long id) {
        Delivery d = require(id);
        if (d.getStatus() != EStatus.FAILED) {
            throw ApiException.badRequest(ENotificationError.NTF_003);
        }
        d.setStatus(EStatus.PENDING);
        d.setAttempts(0);
        d.setLastErrorCode(null);
        d.setNextAttemptAt(Instant.now(clock));
        deliveries.save(d);
        dispatcher.dispatch(List.of(d.getId()));
        return DeliveryDto.of(deliveries.findById(d.getId()).orElse(d));
    }

    private Delivery require(Long id) {
        Delivery d = deliveries.findById(id).orElseThrow(() -> ApiException.notFound(ENotificationError.NTF_002));
        TenantGuard.assertSameCompany(d.getCompanyId(), ENotificationError.NTF_002);
        return d;
    }
}
