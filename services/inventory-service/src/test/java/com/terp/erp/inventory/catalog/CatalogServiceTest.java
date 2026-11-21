package com.terp.erp.inventory.catalog;

import com.terp.erp.exception.ApiException;
import com.terp.erp.inventory.TestLogin;
import com.terp.erp.inventory.common.EInventoryError;
import com.terp.erp.inventory.item.ItemRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CatalogServiceTest {
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final ItemRepository items = mock(ItemRepository.class);
    private final CatalogService service = new CatalogService(categories, mock(TaxRepository.class),
            mock(UnitRepository.class), items);
    private final Category electronics = new Category();

    @BeforeEach
    void setUp() {
        TestLogin.as(10L, "INVENTORY_MANAGE");
        electronics.setId(3L);
        electronics.setCompanyId(10L);
        electronics.setName("Electronics");
        when(categories.findByIdAndDeletedFalse(3L)).thenReturn(Optional.of(electronics));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void categoryInUseIsNotDeleted() {
        when(items.existsByCategoryIdAndDeletedFalse(3L)).thenReturn(true);
        assertCode(() -> service.deleteCategory(3L), EInventoryError.INV_006);
        assertThat(electronics.isDeleted()).isFalse();

        when(items.existsByCategoryIdAndDeletedFalse(3L)).thenReturn(false);
        service.deleteCategory(3L);
        assertThat(electronics.isDeleted()).isTrue();
    }

    @Test
    void nameUniqueAndCompanyIsolated() {
        when(categories.existsByCompanyIdAndNameIgnoreCaseAndDeletedFalseAndIdNot(eq(10L), anyString(), anyLong()))
                .thenReturn(true);
        assertCode(() -> service.createCategory(null, new CatalogService.CategoryForm("Electronics", "#1E88E5")),
                EInventoryError.INV_005);
        electronics.setCompanyId(11L);
        assertCode(() -> service.deleteCategory(3L), EInventoryError.INV_002);
    }

    @Test
    void superAdminMustNameTheCompany() {
        TestLogin.superAdmin();
        assertCode(() -> service.categories(null), EInventoryError.INV_001);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, EInventoryError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
