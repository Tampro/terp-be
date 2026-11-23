package com.terp.erp.commerce.pos;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface PosOrderRepository extends JpaRepository<PosOrder, Long> {
    @Query("""
            select o from PosOrder o where o.companyId = :companyId
              and (:customerId is null or o.customerId = :customerId)
              and (:warehouseId is null or o.warehouseId = :warehouseId)
              and (:from is null or o.orderDate >= :from) and (:to is null or o.orderDate <= :to)
              and (:kw is null or lower(o.number) like :kw or lower(o.customerName) like :kw)""")
    Page<PosOrder> search(@Param("companyId") Long companyId, @Param("customerId") Long customerId,
                          @Param("warehouseId") Long warehouseId, @Param("from") LocalDate from,
                          @Param("to") LocalDate to, @Param("kw") String keyword, Pageable pageable);

    /** [orderDate, count, total] per day. */
    @Query("""
            select o.orderDate, count(o), sum(o.total) from PosOrder o
            where o.companyId = :companyId and o.orderDate between :from and :to
            group by o.orderDate order by o.orderDate""")
    List<Object[]> daily(@Param("companyId") Long companyId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** [warehouseId, warehouseName, count, total] per warehouse. */
    @Query("""
            select o.warehouseId, max(o.warehouseName), count(o), sum(o.total) from PosOrder o
            where o.companyId = :companyId and o.orderDate between :from and :to
            group by o.warehouseId order by sum(o.total) desc""")
    List<Object[]> byWarehouse(@Param("companyId") Long companyId, @Param("from") LocalDate from,
                               @Param("to") LocalDate to);

    /** [itemId, itemName, sku, quantity, revenue (amount + tax), orders] per item, highest revenue first. */
    @Query("""
            select l.itemId, max(l.itemName), max(l.sku), sum(l.quantity), sum(l.amount + l.taxAmount),
                   count(distinct o.id)
            from PosOrder o join o.lines l
            where o.companyId = :companyId and o.orderDate between :from and :to
            group by l.itemId order by sum(l.amount + l.taxAmount) desc""")
    List<Object[]> byItem(@Param("companyId") Long companyId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** [customerId (null = walk-in), customerName, orders, spent, lastOrderDate], highest spend first. */
    @Query("""
            select o.customerId, max(o.customerName), count(o), sum(o.total), max(o.orderDate) from PosOrder o
            where o.companyId = :companyId and o.orderDate between :from and :to
            group by o.customerId order by sum(o.total) desc""")
    List<Object[]> byCustomer(@Param("companyId") Long companyId, @Param("from") LocalDate from,
                              @Param("to") LocalDate to);

    /** [createdDate, total] of each order dated in [from, to]: the hour x weekday heat map of the POS dashboard. */
    @Query("""
            select o.createdDate, o.total from PosOrder o
            where o.companyId = :companyId and o.orderDate between :from and :to""")
    List<Object[]> createdAndTotal(@Param("companyId") Long companyId, @Param("from") LocalDate from,
                                   @Param("to") LocalDate to);
}
