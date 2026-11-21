package com.terp.erp.employee.lifecycle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface TripExpenseRepository extends JpaRepository<TripExpense, Long> {
    List<TripExpense> findByTripIdAndDeletedFalseOrderByExpenseDateAscIdAsc(Long tripId);

    Optional<TripExpense> findByIdAndTripIdAndDeletedFalse(Long id, Long tripId);

    @Query("select coalesce(sum(x.amount), 0) from TripExpense x where x.tripId = :tripId and x.deleted = false")
    BigDecimal total(Long tripId);

    long countByTripIdAndDeletedFalse(Long tripId);

    boolean existsByExpenseTypeIdAndDeletedFalse(Long expenseTypeId);
}
