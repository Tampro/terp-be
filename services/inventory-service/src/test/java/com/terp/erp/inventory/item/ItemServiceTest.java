package com.terp.erp.inventory.item;

import com.terp.erp.exception.ApiException;
import com.terp.erp.inventory.TestLogin;
import com.terp.erp.inventory.catalog.CatalogService;
import com.terp.erp.inventory.catalog.Category;
import com.terp.erp.inventory.catalog.CategoryRepository;
import com.terp.erp.inventory.catalog.TaxRepository;
import com.terp.erp.inventory.catalog.Unit;
import com.terp.erp.inventory.catalog.UnitRepository;
import com.terp.erp.inventory.common.EInventoryError;
import com.terp.erp.inventory.item.Item.EItemType;
import com.terp.erp.inventory.item.ItemService.ItemCreateForm;
import com.terp.erp.inventory.stock.StockService;
import com.terp.erp.inventory.stock.WarehouseStockRepository;
import com.terp.erp.inventory.warehouse.WarehouseService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ItemServiceTest {
    private final ItemRepository items = mock(ItemRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final UnitRepository units = mock(UnitRepository.class);
    private final TaxRepository taxes = mock(TaxRepository.class);
    private final WarehouseStockRepository stocks = mock(WarehouseStockRepository.class);
    private final ItemService service = new ItemService(items, new CatalogService(categories, taxes, units, items),
            categories, units, taxes, mock(WarehouseService.class), mock(StockService.class), stocks);

    @BeforeEach
    void setUp() {
        TestLogin.as(10L, "INVENTORY_MANAGE");
        Category c = new Category();
        c.setId(3L);
        c.setCompanyId(10L);
        c.setName("Electronics");
        Unit u = new Unit();
        u.setId(4L);
        u.setCompanyId(10L);
        u.setName("pcs");
        when(categories.findByIdAndDeletedFalse(3L)).thenReturn(Optional.of(c));
        when(units.findByIdAndDeletedFalse(4L)).thenReturn(Optional.of(u));
        when(stocks.sumByItem(any())).thenReturn(BigDecimal.ZERO);
        when(items.save(any())).thenAnswer(i -> {
            Item it = i.getArgument(0);
            it.setId(9L);
            return it;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anotherCompanysItemIsNotFound() {
        Item other = new Item();
        other.setId(9L);
        other.setCompanyId(11L);
        other.setType(EItemType.PRODUCT);
        when(items.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(other));
        assertCode(() -> service.get(9L), EInventoryError.INV_009);
        assertCode(() -> service.delete(9L), EInventoryError.INV_009);
        assertThatThrownBy(() -> service.get(9L)).satisfies(e ->
                assertThat(((ApiException) e).getStatus().value()).isEqualTo(404));
    }

    @Test
    void generatedSkuIsUniqueInTheCompany() {
        // the first candidate is taken: a new one is drawn
        when(items.existsByCompanyIdAndSkuIgnoreCaseAndDeletedFalseAndIdNot(eq(10L), anyString(), anyLong()))
                .thenReturn(true, false, false);
        var dto = service.create(null, form(null, true));
        assertThat(dto.sku()).matches("[A-Z0-9]{8}");
        verify(items, times(3)).existsByCompanyIdAndSkuIgnoreCaseAndDeletedFalseAndIdNot(eq(10L), anyString(),
                anyLong());
    }

    @Test
    void skuRules() {
        assertCode(() -> service.create(null, form(null, false)), EInventoryError.INV_011);
        when(items.existsByCompanyIdAndSkuIgnoreCaseAndDeletedFalseAndIdNot(eq(10L), eq("MOUSE-01"), anyLong()))
                .thenReturn(true);
        assertCode(() -> service.create(null, form("MOUSE-01", false)), EInventoryError.INV_010);
    }

    @Test
    void referencesMustBeOfTheCompany() {
        when(categories.findByIdAndDeletedFalse(3L)).thenReturn(Optional.empty());
        assertCode(() -> service.create(null, form("X-1", false)), EInventoryError.INV_012);
    }

    private static ItemCreateForm form(String sku, boolean generate) {
        return new ItemCreateForm(EItemType.PRODUCT, "Wireless mouse", sku, generate, null, 3L, 4L,
                new BigDecimal("250000"), new BigDecimal("180000"), null, null, null, null);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, EInventoryError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
