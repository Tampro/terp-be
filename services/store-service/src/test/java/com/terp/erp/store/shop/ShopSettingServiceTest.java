package com.terp.erp.store.shop;

import com.terp.erp.exception.ApiException;
import com.terp.erp.exception.ECommonError;
import com.terp.erp.exception.ErrorCode;
import com.terp.erp.store.TestLogin;
import com.terp.erp.store.common.EStoreError;
import com.terp.erp.store.shop.ShopSettingService.ShopSettingForm;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopSettingServiceTest {
    private final ShopSettingRepository settings = mock(ShopSettingRepository.class);
    private final PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
    private final ShopSettingService service = new ShopSettingService(settings, tx);

    @BeforeEach
    void setUp() {
        TestLogin.as(10L, "STORE_MANAGE");
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(settings.findByCompanyId(10L)).thenReturn(Optional.empty());
        when(settings.saveAndFlush(any())).thenAnswer(a -> a.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getWithoutRowGivesTheDefaultAndWritesNothing() {
        ShopSettingService.ShopSettingDto d = service.get(null);
        assertThat(d.companyId()).isEqualTo("10");
        assertThat(d.slug()).isNull();
        assertThat(d.enabled()).isFalse();
        verify(settings, never()).saveAndFlush(any());
    }

    @Test
    void createNormalisesTheSlug() {
        ShopSettingService.ShopSettingDto d = service.put(null, new ShopSettingForm("  Express-0293 ", true));
        assertThat(d.slug()).isEqualTo("express-0293");
        assertThat(d.enabled()).isTrue();
    }

    @Test
    void replaceKeepsTheRow() {
        ShopSetting s = new ShopSetting();
        s.setId(5L);
        s.setCompanyId(10L);
        s.setSlug("old-shop");
        when(settings.findByCompanyId(10L)).thenReturn(Optional.of(s));
        service.put(null, new ShopSettingForm("new-shop", false));
        assertThat(s.getId()).isEqualTo(5L);
        assertThat(s.getSlug()).isEqualTo("new-shop");
        assertThat(s.isEnabled()).isFalse();
    }

    @Test
    void reservedAndMalformedSlugs() {
        assertCode(() -> service.put(null, new ShopSettingForm("Admin", true)), EStoreError.STO_015);
        for (String bad : new String[]{"-bad", "bad-", "ab", "a".repeat(41), "a_b", "가나다"}) {
            assertCode(() -> service.put(null, new ShopSettingForm(bad, true)), ECommonError.COMMON_001);
        }
        verify(settings, never()).saveAndFlush(any());
    }

    @Test
    void slugOfAnotherCompanyIsRefusedAlsoOnARace() {
        when(settings.existsBySlugAndCompanyIdNot("taken", 10L)).thenReturn(true);
        assertCode(() -> service.put(null, new ShopSettingForm("taken", true)), EStoreError.STO_014);
        when(settings.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("Duplicate entry for key 'uk_shop_setting_slug'"));
        assertCode(() -> service.put(null, new ShopSettingForm("racing", true)), EStoreError.STO_014);
    }

    @Test
    void sameCompanyFirstSaveRaceIsRetriedOnce() {
        ShopSetting existing = new ShopSetting();
        existing.setCompanyId(10L);
        when(settings.findByCompanyId(10L)).thenReturn(Optional.empty(), Optional.of(existing));
        when(settings.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry for key 'uk_shop_setting_company'"))
                .thenAnswer(a -> a.getArgument(0));
        ShopSettingService.ShopSettingDto d = service.put(null, new ShopSettingForm("my-shop", true));
        assertThat(d.slug()).isEqualTo("my-shop");
        assertThat(existing.getSlug()).isEqualTo("my-shop");
    }

    @Test
    void superAdminMustNameTheCompany() {
        TestLogin.superAdmin();
        assertCode(() -> service.get(null), EStoreError.STO_007);
        assertCode(() -> service.put(null, new ShopSettingForm("any-shop", true)), EStoreError.STO_007);
    }

    private static void assertCode(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
