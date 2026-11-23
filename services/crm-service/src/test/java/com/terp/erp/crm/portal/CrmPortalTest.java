package com.terp.erp.crm.portal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import com.terp.erp.crm.TestLogin;
import com.terp.erp.crm.client.AccountingClient;
import com.terp.erp.crm.client.RemoteApiException;
import com.terp.erp.crm.contract.Contract;
import com.terp.erp.crm.contract.ContractController;
import com.terp.erp.crm.contract.ContractPortalService;
import com.terp.erp.crm.contract.ContractRenewalRepository;
import com.terp.erp.crm.contract.ContractRepository;
import com.terp.erp.crm.contract.ContractTypeRepository;
import com.terp.erp.crm.contract.ContractTypeService;
import com.terp.erp.crm.contract.EContractError;
import com.terp.erp.crm.record.CrmEnums.ELinkType;
import com.terp.erp.crm.record.CrmEnums.ESubject;
import com.terp.erp.crm.record.CrmLinkRepository;
import com.terp.erp.crm.record.Deal;
import com.terp.erp.crm.record.DealPortalService;
import com.terp.erp.crm.record.DealRepository;
import com.terp.erp.crm.common.ECrmError;
import com.terp.erp.crm.setup.SetupService;
import com.terp.erp.exception.ApiException;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Client portal: only rows of the customer linked to me; another customer's id and "not linked" answer 404. */
class CrmPortalTest {
    private final ContractRepository contracts = mock(ContractRepository.class);
    private final ContractRenewalRepository renewals = mock(ContractRenewalRepository.class);
    private final DealRepository deals = mock(DealRepository.class);
    private final CrmLinkRepository links = mock(CrmLinkRepository.class);
    private final SetupService setup = mock(SetupService.class);
    private final AccountingClient accounting = mock(AccountingClient.class);
    private final ContractPortalService contractPortal = new ContractPortalService(contracts, renewals,
            new ContractTypeService(mock(ContractTypeRepository.class), contracts), accounting);
    private final DealPortalService dealPortal = new DealPortalService(deals, links, setup, accounting);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Contract contract(long id, long company, long customer) {
        Contract c = new Contract();
        c.setId(id);
        c.setCompanyId(company);
        c.setCustomerId(customer);
        c.setNumber("CON000" + id);
        c.setSubject("Test contract");
        c.setContractTypeId(2L);
        c.setValue(BigDecimal.TEN);
        c.setStartDate(LocalDate.of(2026, 12, 1));
        c.setEndDate(LocalDate.of(2027, 12, 1));
        when(contracts.findByIdAndDeletedFalse(id)).thenReturn(Optional.of(c));
        return c;
    }

    @Test
    void contractListIsOfMyCustomerOnly() {
        TestLogin.user(7L, "kc-7", 10L, "PORTAL_CLIENT");
        when(accounting.myCustomerId()).thenReturn(5L);
        Contract mine = contract(1L, 10L, 5L);
        when(contracts.findByCompanyIdAndCustomerIdAndDeletedFalse(eq(10L), eq(5L), any()))
                .thenReturn(new PageImpl<>(List.of(mine)));

        assertThat(contractPortal.list(Pageable.unpaged()).getContent())
                .extracting(ContractPortalService.PortalContract::number).containsExactly("CON0001");
    }

    @Test
    void anotherCustomersContractAnswers404() {
        TestLogin.user(7L, "kc-7", 10L, "PORTAL_CLIENT");
        when(accounting.myCustomerId()).thenReturn(5L);
        contract(1L, 10L, 6L);
        contract(2L, 20L, 5L);
        contract(3L, 10L, 5L);
        when(renewals.findByContractIdAndDeletedFalseOrderByIdAsc(3L)).thenReturn(List.of());

        for (long id : new long[]{1L, 2L}) {
            assertThatThrownBy(() -> contractPortal.get(id)).isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EContractError.CTR_002);
        }
        assertThat(contractPortal.get(3L).contract().id()).isEqualTo("3");
    }

    @Test
    void notLinkedAnswers404FromAccounting() {
        TestLogin.user(7L, "kc-7", 10L, "PORTAL_CLIENT");
        when(accounting.myCustomerId()).thenThrow(new RemoteApiException(HttpStatus.NOT_FOUND, "ACC_098", "not linked"));

        assertThatThrownBy(() -> contractPortal.list(Pageable.unpaged())).isInstanceOf(RemoteApiException.class)
                .extracting(e -> ((RemoteApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThatThrownBy(() -> dealPortal.get(40L)).isInstanceOf(RemoteApiException.class);
    }

    @Test
    void dealsAreThoseWithMyCustomerAsClient() {
        TestLogin.user(7L, "kc-7", 10L, "PORTAL_CLIENT");
        when(accounting.myCustomerId()).thenReturn(5L);
        when(setup.stageNames(anyCollection())).thenReturn(Map.of(5L, "Won"));
        Deal d = new Deal();
        d.setId(40L);
        d.setCompanyId(10L);
        d.setStageId(5L);
        d.setName("Test Deal");
        when(deals.findOfClient(eq(10L), eq(5L), eq(ESubject.DEAL), eq(ELinkType.CLIENT), any()))
                .thenReturn(new PageImpl<>(List.of(d)));
        when(deals.findByIdAndDeletedFalse(40L)).thenReturn(Optional.of(d));
        when(links.existsBySubjectTypeAndSubjectIdAndLinkTypeAndRefId(ESubject.DEAL, 40L, ELinkType.CLIENT, 5L))
                .thenReturn(true);
        Deal other = new Deal();
        other.setId(41L);
        other.setCompanyId(10L);
        when(deals.findByIdAndDeletedFalse(41L)).thenReturn(Optional.of(other));

        assertThat(dealPortal.list(Pageable.unpaged()).getContent()).extracting(DealPortalService.PortalDeal::stageName)
                .containsExactly("Won");
        assertThat(dealPortal.get(40L).name()).isEqualTo("Test Deal");
        assertThatThrownBy(() -> dealPortal.get(41L)).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ECrmError.CRM_007);
    }

    private static List<Method> handlers(Class<?> c) {
        return Arrays.stream(c.getDeclaredMethods()).filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> Arrays.stream(m.getAnnotations()).anyMatch(a -> a.annotationType()
                        .isAnnotationPresent(org.springframework.web.bind.annotation.RequestMapping.class)))
                .toList();
    }

    @Test
    void portalHandlersNeedPortalClientAndAreReadOnly() {
        List<Method> hs = handlers(ClientPortalController.class);
        assertThat(hs).hasSize(4).allSatisfy(m -> {
            assertThat(m.getAnnotation(PreAuthorize.class).value()).isEqualTo("@PermissionChecker.has('PORTAL_CLIENT')");
            assertThat(m.isAnnotationPresent(GetMapping.class)).isTrue();
        });
    }

    @Test
    void contractAndLeadDealHandlersAcceptTheOwnCode() {
        for (Method m : handlers(ContractController.class)) {
            assertThat(m.getAnnotation(PreAuthorize.class).value()).as(m.getName()).contains("_OWN')");
        }
        for (Class<?> c : com.terp.erp.crm.record.CrmPermissionView.ownScoped()) {
            for (Method m : handlers(c)) {
                assertThat(m.getAnnotation(PreAuthorize.class).value()).as(c.getSimpleName() + "." + m.getName())
                        .matches("@PermissionChecker\\.hasAny\\('CRM_(VIEW|MANAGE)','CRM_\\1_OWN'\\)");
            }
        }
    }
}
