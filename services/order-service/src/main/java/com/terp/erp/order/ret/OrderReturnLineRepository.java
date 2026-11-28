package com.terp.erp.order.ret;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderReturnLineRepository extends JpaRepository<OrderReturnLine, Long> {
    List<OrderReturnLine> findByReturnIdOrderByIdAsc(Long returnId);

    /** Units of each order line already returned or in an open return (rejected returns free their lines). */
    @Query("select l.orderLineId, sum(l.quantity) from OrderReturnLine l, OrderReturn r where l.returnId = r.id "
            + "and r.orderId = :orderId and r.status <> com.terp.erp.order.ret.EReturnStatus.REJECTED group by l.orderLineId")
    List<Object[]> takenByOrderLine(@Param("orderId") Long orderId);
}
