package com.terp.erp.inventory.item;

import com.terp.erp.inventory.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** An additional image of an item: a media-service file id (the main image is Item.mainImageId). */
@Getter
@Setter
@Entity
@Table(name = "item_image")
public class ItemImage extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "media_file_id", nullable = false)
    private Long mediaFileId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
