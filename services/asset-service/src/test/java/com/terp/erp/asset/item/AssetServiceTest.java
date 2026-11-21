package com.terp.erp.asset.item;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.asset.TestLogin;
import com.terp.erp.asset.client.MediaClient;
import com.terp.erp.asset.common.EAssetError;
import com.terp.erp.asset.item.Asset.EStatus;
import com.terp.erp.asset.item.AssetService.AssetForm;
import com.terp.erp.asset.type.AssetType;
import com.terp.erp.asset.type.AssetTypeRepository;
import com.terp.erp.asset.type.AssetTypeService;
import com.terp.erp.exception.ApiException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssetServiceTest {
    private final AssetRepository assets = mock(AssetRepository.class);
    private final AssetTypeRepository types = mock(AssetTypeRepository.class);
    private final MediaClient media = mock(MediaClient.class);
    private final AssetService service = new AssetService(assets, types, media,
            mock(com.terp.erp.asset.assignment.CurrentAssignments.class));
    private final AssetTypeService typeService = new AssetTypeService(types, assets);
    private final AssetType laptop = new AssetType();

    @BeforeEach
    void setUp() {
        TestLogin.as(10L, "ASSET_MANAGE");
        laptop.setId(3L);
        laptop.setCompanyId(10L);
        laptop.setName("Laptops");
        when(types.findByIdAndDeletedFalse(3L)).thenReturn(Optional.of(laptop));
        when(types.findById(3L)).thenReturn(Optional.of(laptop));
        when(assets.save(any())).thenAnswer(i -> {
            Asset a = i.getArgument(0);
            a.setId(9L);
            return a;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createDefaultsAndChecksFiles() {
        var dto = service.create(null, form(null, "1000", "100", Set.of(41L)));
        assertThat(dto.status()).isEqualTo(EStatus.AVAILABLE);
        assertThat(dto.condition()).isEqualTo(Asset.ECondition.GOOD);
        assertThat(dto.depreciationMethod()).isEqualTo(Asset.EDepreciationMethod.STRAIGHT_LINE);
        assertThat(dto.usefulLifeMonths()).isEqualTo(48);
        assertThat(dto.assetType().name()).isEqualTo("Laptops");
        verify(media).require(41L, 10L);
    }

    @Test
    void formRules() {
        assertCode(() -> service.create(null, form(null, "100", "101", Set.of())), EAssetError.AST_010);
        assertCode(() -> service.create(null, form(EStatus.ASSIGNED, "1", "0", Set.of())), EAssetError.AST_015);
        laptop.setCompanyId(11L);
        assertCode(() -> service.create(null, form(null, "1", "0", Set.of())), EAssetError.AST_002);
        laptop.setCompanyId(10L);
        when(assets.existsByCompanyIdAndAssetCodeIgnoreCaseAndDeletedFalseAndIdNot(eq(10L), eq("AST-1"), anyLong()))
                .thenReturn(true);
        assertCode(() -> service.create(null, form(null, "1", "0", Set.of())), EAssetError.AST_006);
    }

    @Test
    void assignedAssetKeepsItsStatusUntilReturned() {
        Asset a = new Asset();
        a.setId(9L);
        a.setCompanyId(10L);
        a.setStatus(EStatus.ASSIGNED);
        a.getDocumentFileIds().add(41L);
        when(assets.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(a));
        assertCode(() -> service.update(9L, form(EStatus.DISPOSED, "1", "0", Set.of(41L))), EAssetError.AST_016);
        assertCode(() -> service.delete(9L), EAssetError.AST_016);
        // status omitted = kept; an already attached document is not checked again
        assertThat(service.update(9L, form(null, "1", "0", Set.of(41L))).status()).isEqualTo(EStatus.ASSIGNED);
        verify(media, never()).require(eq(41L), any());
        a.setCompanyId(11L);
        assertCode(() -> service.get(9L), EAssetError.AST_005);
    }

    @Test
    void typeRules() {
        when(types.existsByCompanyIdAndNameIgnoreCaseAndDeletedFalseAndIdNot(eq(10L), anyString(), anyLong()))
                .thenReturn(true);
        assertCode(() -> typeService.create(null, new AssetTypeService.TypeForm("Laptops", null)), EAssetError.AST_003);
        when(assets.countByAssetTypeIdAndDeletedFalse(3L)).thenReturn(2L);
        assertCode(() -> typeService.delete(3L), EAssetError.AST_004);
    }

    private static AssetForm form(EStatus status, String cost, String salvage, Set<Long> docs) {
        return new AssetForm("Test laptop", 3L, "SN-1", "AST-1", LocalDate.of(2026, 1, 15), new BigDecimal(cost),
                status, null, null, "Head office", null, null, LocalDate.of(2029, 1, 15), null, docs, null, 4,
                new BigDecimal(salvage));
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, EAssetError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
