package com.terp.erp.inventory.warehouse;

import com.terp.erp.inventory.TestLogin;
import com.terp.erp.inventory.stock.WarehouseStockRepository;
import com.terp.erp.outbox.OutboxPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WarehouseEventTest {
    private final WarehouseRepository warehouses = mock(WarehouseRepository.class);
    private final OutboxPublisher outbox = mock(OutboxPublisher.class);
    private final WarehouseService service = new WarehouseService(warehouses, mock(WarehouseStockRepository.class),
            outbox);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createPublishesWarehouseCreated() {
        TestLogin.as(10L, "INVENTORY_MANAGE");
        when(warehouses.save(any())).thenAnswer(a -> {
            Warehouse w = a.getArgument(0);
            w.setId(7L);
            return w;
        });
        service.create(null, new WarehouseService.WarehouseForm("Main", "1 Example Street", "Testville", "00000",
                null, null, null));
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outbox).publish(eq(10L), eq("inventory.warehouse.created"), eq("WAREHOUSE"), eq(7L), payload.capture());
        @SuppressWarnings("unchecked") Map<String, Object> p = (Map<String, Object>) payload.getValue();
        assertThat(p).containsEntry("warehouseId", "7").containsEntry("title", "Main").containsKeys("city", "active");
    }
}
