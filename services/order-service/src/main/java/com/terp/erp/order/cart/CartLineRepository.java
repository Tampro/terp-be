package com.terp.erp.order.cart;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CartLineRepository extends JpaRepository<CartLine, Long> {
    List<CartLine> findByCartIdOrderByIdAsc(Long cartId);

    Optional<CartLine> findByCartIdAndItemId(Long cartId, Long itemId);

    long countByCartId(Long cartId);

    @Modifying
    @Query("delete from CartLine l where l.cartId = :cartId")
    int deleteOfCart(Long cartId);

    /** Lines not changed since {@code before} and not kept (90-day clean-up). */
    @Modifying
    @Query("delete from CartLine l where l.keep = false and l.updatedDate < :before")
    int deleteStale(Instant before);
}
