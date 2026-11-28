package com.terp.erp.delivery.delivery;

import com.terp.erp.delivery.client.IamClient;
import com.terp.erp.delivery.client.OrderClient;
import com.terp.erp.delivery.common.EDeliveryError;
import com.terp.erp.delivery.delivery.DeliveryDtos.DeliveryDetailDto;
import com.terp.erp.delivery.delivery.DeliveryDtos.DeliveryDto;
import com.terp.erp.delivery.delivery.DeliveryDtos.DestinationDto;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.ErpPrincipal;
import com.terp.erp.security.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Set;
import java.util.function.Consumer;

/** Back Office (list, detail, assign, cancel) and the rider's own deliveries (pick up, deliver). */
@Service
@RequiredArgsConstructor
public class DeliveryService {
    private static final Set<EDeliveryStatus> OPEN = Set.of(EDeliveryStatus.READY, EDeliveryStatus.ASSIGNED);

    private final DeliveryRepository deliveries;
    private final OrderClient orders;
    private final IamClient iam;
    private final Clock clock;

    // ------------------------------------------------------------------ Back Office

    @Transactional(readOnly = true)
    public Page<DeliveryDto> list(Long requestedCompanyId, Long storeId, EDeliveryStatus status, String riderSub,
                                  Pageable pageable) {
        Long company = TenantGuard.resolveCompany(requestedCompanyId, EDeliveryError.DLV_002);
        return deliveries.search(company, storeId, status, riderSub, pageable).map(DeliveryDto::of);
    }

    @Transactional(readOnly = true)
    public DeliveryDetailDto get(Long id) {
        Delivery d = require(id);
        return new DeliveryDetailDto(DeliveryDto.of(d), DestinationDto.of(orders.order(d.getCompanyId(), d.getOrderId()), true));
    }

    @Transactional
    public DeliveryDto assign(Long id, long riderUserId) {
        return change(() -> {
            Delivery d = require(id);
            state(d, OPEN);
            IamClient.Rider r = iam.rider(d.getCompanyId(), riderUserId);
            if (!r.active() || !r.canRide() || r.keycloakSub() == null) {
                throw ApiException.badRequest(EDeliveryError.DLV_004);
            }
            d.setRiderSub(r.keycloakSub());
            d.setRiderName(r.fullName());
            d.setStatus(EDeliveryStatus.ASSIGNED);
            d.setAssignedAt(clock.instant());
            return d;
        });
    }

    @Transactional
    public DeliveryDto cancel(Long id, String reason) {
        return change(() -> {
            Delivery d = require(id);
            state(d, OPEN);
            d.setStatus(EDeliveryStatus.CANCELLED);
            d.setCancelledAt(clock.instant());
            d.setCancelReason(reason == null || reason.isBlank() ? null : reason.trim());
            return d;
        });
    }

    // ------------------------------------------------------------------ rider (own deliveries only)

    @Transactional(readOnly = true)
    public Page<DeliveryDto> mine(EDeliveryStatus status, Pageable pageable) {
        ErpPrincipal me = CurrentUser.get();
        if (me.companyId() == null) {
            return Page.empty(pageable);
        }
        return deliveries.search(me.companyId(), null, status, me.keycloakId(), pageable).map(DeliveryDto::of);
    }

    @Transactional(readOnly = true)
    public DeliveryDetailDto myDelivery(Long id) {
        Delivery d = own(id);
        return new DeliveryDetailDto(DeliveryDto.of(d), DestinationDto.of(orders.order(d.getCompanyId(), d.getOrderId()), false));
    }

    @Transactional
    public DeliveryDto pickUp(Long id) {
        return step(id, EDeliveryStatus.ASSIGNED, d -> {
            d.setStatus(EDeliveryStatus.PICKED_UP);
            d.setPickedUpAt(clock.instant());
        });
    }

    @Transactional
    public DeliveryDto deliver(Long id) {
        return step(id, EDeliveryStatus.PICKED_UP, d -> {
            d.setStatus(EDeliveryStatus.DELIVERED);
            d.setDeliveredAt(clock.instant());
        });
    }

    // ------------------------------------------------------------------ helpers

    private DeliveryDto step(Long id, EDeliveryStatus from, Consumer<Delivery> to) {
        return change(() -> {
            Delivery d = own(id);
            state(d, Set.of(from));
            to.accept(d);
            return d;
        });
    }

    private DeliveryDto change(java.util.function.Supplier<Delivery> work) {
        try {
            return DeliveryDto.of(deliveries.saveAndFlush(work.get()));
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ApiException(HttpStatus.CONFLICT, EDeliveryError.DLV_006);
        }
    }

    private static void state(Delivery d, Set<EDeliveryStatus> allowed) {
        if (!allowed.contains(d.getStatus())) {
            throw ApiException.badRequest(EDeliveryError.DLV_005, d.getStatus().name());
        }
    }

    private Delivery require(Long id) {
        Delivery d = deliveries.findById(id).orElseThrow(() -> ApiException.notFound(EDeliveryError.DLV_001));
        TenantGuard.assertSameCompany(d.getCompanyId(), EDeliveryError.DLV_001);
        return d;
    }

    /** The caller's own delivery: same company and assigned to the caller's sub; anything else is a 404. */
    private Delivery own(Long id) {
        ErpPrincipal me = CurrentUser.get();
        if (me.companyId() == null) {
            throw ApiException.notFound(EDeliveryError.DLV_001);
        }
        return deliveries.findByIdAndCompanyIdAndRiderSub(id, me.companyId(), me.keycloakId())
                .orElseThrow(() -> ApiException.notFound(EDeliveryError.DLV_001));
    }
}
