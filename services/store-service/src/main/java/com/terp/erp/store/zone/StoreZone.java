package com.terp.erp.store.zone;

import com.terp.erp.store.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;

/** A delivery zone of a store. The polygon travels as GeoJSON; MySQL converts it, so no spatial library is needed. */
@Getter
@Setter
@Entity
@Table(name = "store_zone")
public class StoreZone extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** GeoJSON Polygon text ([lon, lat] positions). */
    @ColumnTransformer(read = "ST_AsGeoJSON(area)", write = "ST_GeomFromGeoJSON(?, 1, 4326)")
    @Column(name = "area", nullable = false, columnDefinition = "POLYGON")
    private String area;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
