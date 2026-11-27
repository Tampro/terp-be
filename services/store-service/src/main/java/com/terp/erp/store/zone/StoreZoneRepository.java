package com.terp.erp.store.zone;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface StoreZoneRepository extends JpaRepository<StoreZone, Long> {
    List<StoreZone> findByStoreIdOrderById(Long storeId);

    Optional<StoreZone> findByIdAndStoreId(Long id, Long storeId);

    /** MySQL decides validity (self-intersection etc.) on the same SRID the column uses. */
    @Query(value = "SELECT ST_IsValid(ST_GeomFromGeoJSON(:json, 1, 4326))", nativeQuery = true)
    Integer isValid(String json);

    interface Hit {
        Long getStoreId();

        Long getZoneId();
    }

    /**
     * The live, active store of the company with an active zone touching the point (an edge counts as inside), nearest
     * to the point by its own coordinates; stores without coordinates last, tie on the smaller id. The point uses
     * GeoJSON like the zones, so both sides share one axis convention.
     */
    @Query(value = "SELECT z.store_id AS storeId, z.id AS zoneId FROM store_zone z JOIN store s ON s.id = z.store_id "
            + "WHERE z.company_id = :company AND z.active = 1 AND s.deleted = 0 AND s.active = 1 "
            + "AND ST_Intersects(z.area, ST_GeomFromGeoJSON(CONCAT('{\"type\":\"Point\",\"coordinates\":[', :lon, ',', :lat, ']}'), 1, 4326)) "
            + "ORDER BY (s.lat IS NULL), "
            + "ST_Distance_Sphere(POINT(:lon, :lat), POINT(COALESCE(s.lon, 0), COALESCE(s.lat, 0))), s.id, z.id LIMIT 1",
            nativeQuery = true)
    Optional<Hit> resolve(Long company, BigDecimal lat, BigDecimal lon);
}
