package com.terp.erp.iam.saas;

import org.junit.jupiter.api.Test;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.model.Plan;
import com.terp.erp.iam.repository.PlanRepository;
import com.terp.erp.iam.saas.SaasDtos.PlanForm;
import com.terp.erp.iam.security.LocalAuthorityResolver;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlanFreeBadgeTest {
    private final PlanRepository plans = mock(PlanRepository.class);
    private final CatalogService service = new CatalogService(plans, mock(LocalAuthorityResolver.class));

    private static PlanForm form(boolean free, String monthly, String yearly, String badge) {
        return new PlanForm("FREE", "Free", null, new BigDecimal(monthly), yearly == null ? null : new BigDecimal(yearly),
                3, null, new BigDecimal("-1"), 0, false, false, true, true, free, badge, null);
    }

    @Test
    void freePlanWithAPriceIsRefused() {
        when(plans.existsByCodeAndDeletedFalse("FREE")).thenReturn(false);
        for (PlanForm f : new PlanForm[]{form(true, "1", null, null), form(true, "0", "100", null)}) {
            assertThatThrownBy(() -> service.createPlan(f))
                    .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ESaasError.SAAS_041);
        }
        verify(plans, never()).save(any());
    }

    @Test
    void freePlanAndBadgeAreStoredAndReturned() {
        when(plans.save(any())).thenAnswer(i -> {
            Plan p = i.getArgument(0);
            p.setId(1L);
            return p;
        });
        var dto = service.createPlan(form(true, "0", "0", "  Most Popular "));
        assertThat(dto.free()).isTrue();
        assertThat(dto.badge()).isEqualTo("Most Popular");
        assertThat(dto.recommended()).isTrue();
        assertThat(service.createPlan(form(false, "490000", null, " ")).badge()).isNull();
    }
}
