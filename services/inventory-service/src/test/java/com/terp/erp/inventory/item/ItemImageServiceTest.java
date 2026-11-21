package com.terp.erp.inventory.item;

import com.terp.erp.exception.ApiException;
import com.terp.erp.inventory.TestLogin;
import com.terp.erp.inventory.client.MediaClient;
import com.terp.erp.inventory.common.EInventoryError;
import com.terp.erp.inventory.item.Item.EItemType;
import com.terp.erp.inventory.item.ItemImageService.ImagesForm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ItemImageServiceTest {
    private final ItemRepository itemRepo = mock(ItemRepository.class);
    private final ItemImageRepository images = mock(ItemImageRepository.class);
    private final MediaClient media = mock(MediaClient.class);
    private final ItemService items = new ItemService(itemRepo, null, null, null, null, null, null, null);
    private final ItemImageService service = new ItemImageService(items, images, media);
    private Item item;

    @BeforeEach
    void setUp() {
        item = new Item();
        item.setId(9L);
        item.setCompanyId(10L);
        item.setType(EItemType.PRODUCT);
        when(itemRepo.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(item));
        when(images.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void replacesMainAndAdditionalImagesInOrderWithoutDuplicates() {
        TestLogin.as(10L, "INVENTORY_MANAGE");
        var dto = service.replace(9L, new ImagesForm(12L, List.of(14L, 13L, 14L)));
        assertThat(dto.mainImageId()).isEqualTo("12");
        assertThat(dto.additionalImageIds()).containsExactly("14", "13");
        assertThat(item.getMainImageId()).isEqualTo(12L);
        verify(media).requireCompanyImage(12L, 10L);
        verify(images).deleteByItemId(9L);
    }

    @Test
    void aFileThatIsNotAnImageOfTheCompanyWritesNothing() {
        TestLogin.as(10L, "INVENTORY_MANAGE");
        item.setMainImageId(5L);
        when(media.requireCompanyImage(eq(13L), anyLong()))
                .thenThrow(ApiException.badRequest(EInventoryError.INV_024, 13L));
        assertThatThrownBy(() -> service.replace(9L, new ImagesForm(12L, List.of(13L))))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EInventoryError.INV_024);
        assertThat(item.getMainImageId()).isEqualTo(5L);
        verify(images, never()).deleteByItemId(any());
    }

    @Test
    void anotherCompanysItemIsNotFound() {
        TestLogin.as(11L, "INVENTORY_MANAGE");
        assertThatThrownBy(() -> service.replace(9L, new ImagesForm(null, List.of()))).satisfies(e -> {
            assertThat(((ApiException) e).getErrorCode()).isEqualTo(EInventoryError.INV_009);
            assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        });
        assertThatThrownBy(() -> service.get(9L))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EInventoryError.INV_009);
        verify(media, never()).requireCompanyImage(any(), any());
    }
}
