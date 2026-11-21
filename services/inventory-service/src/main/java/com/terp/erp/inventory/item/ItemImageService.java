package com.terp.erp.inventory.item;

import com.terp.erp.inventory.client.MediaClient;
import com.terp.erp.inventory.common.Texts;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Item images (manual §9 step 3): one main image plus additional ones, all files of the item's company in
 * media-service. The files themselves (upload, signed links) stay in media-service.
 */
@Service
@RequiredArgsConstructor
public class ItemImageService {
    // TODO(assumption): the manual gives no limit; 10 additional images keeps a product page reasonable.
    static final int MAX_ADDITIONAL = 10;

    private final ItemService items;
    private final ItemImageRepository images;
    private final MediaClient media;

    public record ImagesForm(
            @Schema(description = "media-service file id of the main image; null removes it", example = "12")
            @Positive Long mainImageId,
            @Schema(description = "media-service file ids of the additional images, in display order; "
                    + "empty removes them", example = "[13, 14]")
            @Size(max = MAX_ADDITIONAL) List<@NotNull @Positive Long> additionalImageIds) {
    }

    public record ImagesDto(String itemId, String mainImageId, List<String> additionalImageIds) {
    }

    @Transactional(readOnly = true)
    public ImagesDto get(Long itemId) {
        Item i = items.require(itemId);
        return dto(i, images.findByItemIdOrderBySortOrderAsc(i.getId()));
    }

    /** Full replacement of the main and additional images. Every id is checked before anything is written. */
    @Transactional
    public ImagesDto replace(Long itemId, ImagesForm f) {
        Item i = items.require(itemId);
        Set<Long> additional = new LinkedHashSet<>(f.additionalImageIds() == null ? List.of() : f.additionalImageIds());
        if (f.mainImageId() != null) {
            media.requireCompanyImage(f.mainImageId(), i.getCompanyId());
        }
        for (Long id : additional) {
            media.requireCompanyImage(id, i.getCompanyId());
        }
        i.setMainImageId(f.mainImageId());
        images.deleteByItemId(i.getId());
        List<ItemImage> rows = new ArrayList<>();
        int order = 0;
        for (Long id : additional) {
            ItemImage img = new ItemImage();
            img.setCompanyId(i.getCompanyId());
            img.setItemId(i.getId());
            img.setMediaFileId(id);
            img.setSortOrder(order++);
            rows.add(img);
        }
        return dto(i, images.saveAll(rows));
    }

    private static ImagesDto dto(Item i, List<ItemImage> rows) {
        return new ImagesDto(i.getId().toString(), Texts.id(i.getMainImageId()),
                rows.stream().map(r -> r.getMediaFileId().toString()).toList());
    }
}
