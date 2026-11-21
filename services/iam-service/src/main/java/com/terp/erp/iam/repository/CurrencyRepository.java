package com.terp.erp.iam.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.terp.erp.iam.model.Currency;

import java.util.List;
import java.util.Optional;

public interface CurrencyRepository extends JpaRepository<Currency, Long> {
    boolean existsByCode(String code);

    Optional<Currency> findByCode(String code);

    Optional<Currency> findFirstByDefaultCurrencyTrue();

    List<Currency> findAllByOrderByDefaultCurrencyDescCodeAsc();

    /** Search by name, code or symbol (docs/analysis/06 §3.2.5). */
    @Query("select c from Currency c where :keyword = ''"
            + " or lower(c.name) like lower(concat('%', :keyword, '%'))"
            + " or lower(c.code) like lower(concat('%', :keyword, '%'))"
            + " or lower(coalesce(c.symbol, '')) like lower(concat('%', :keyword, '%'))")
    Page<Currency> search(String keyword, Pageable pageable);
}
