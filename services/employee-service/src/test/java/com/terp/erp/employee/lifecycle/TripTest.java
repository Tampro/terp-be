package com.terp.erp.employee.lifecycle;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.employee.TestLogin;
import com.terp.erp.employee.client.MediaClient;
import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.employee.employee.EEmployeeStatus;
import com.terp.erp.employee.employee.Employee;
import com.terp.erp.employee.employee.EmployeeRepository;
import com.terp.erp.employee.lifecycle.EmployeeTrip.EAdvanceStatus;
import com.terp.erp.employee.lifecycle.EmployeeTrip.EReimbursementStatus;
import com.terp.erp.employee.lifecycle.EmployeeTrip.EStatus;
import com.terp.erp.employee.lifecycle.TripExpenseService.ExpenseForm;
import com.terp.erp.employee.lifecycle.TripService.TripForm;
import com.terp.erp.exception.ApiException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TripTest {
    private static final LocalDate START = LocalDate.of(2026, 12, 1);

    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final EmployeeTripRepository tripRepo = mock(EmployeeTripRepository.class);
    private final TripExpenseRepository expenseRepo = mock(TripExpenseRepository.class);
    private final LifecycleCatalogRepository items = mock(LifecycleCatalogRepository.class);
    private final LifecycleSupport support = new LifecycleSupport(employees, mock(MediaClient.class));
    private final LifecycleCatalogService catalogs = new LifecycleCatalogService(items,
            mock(EmployeeWarningRepository.class), mock(EmployeeTerminationRepository.class), expenseRepo,
            mock(EmployeeComplaintRepository.class));
    private final TripService trips = new TripService(tripRepo, expenseRepo, support);
    private final TripExpenseService expenses = new TripExpenseService(expenseRepo, trips, catalogs, items, support);

    private EmployeeTrip trip;

    @BeforeEach
    void setUp() {
        TestLogin.as(10L, "LIFECYCLE_MANAGE");
        Employee e = new Employee();
        e.setId(1L);
        e.setCompanyId(10L);
        e.setFullName("Test One");
        e.setStatus(EEmployeeStatus.ACTIVE);
        when(employees.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(e));
        when(employees.findAllById(any())).thenReturn(List.of(e));
        when(tripRepo.save(any())).thenAnswer(i -> {
            EmployeeTrip t = i.getArgument(0);
            t.setId(9L);
            return t;
        });
        LifecycleCatalog type = new LifecycleCatalog();
        type.setId(4L);
        type.setCompanyId(10L);
        type.setKind(ELifecycleCatalogKind.EXPENSE_TYPE);
        type.setName("Taxi");
        when(items.findByIdAndKindAndDeletedFalse(4L, ELifecycleCatalogKind.EXPENSE_TYPE)).thenReturn(Optional.of(type));
        when(items.findAllById(any())).thenReturn(List.of(type));
        trip = new EmployeeTrip();
        trip.setId(9L);
        trip.setCompanyId(10L);
        trip.setEmployeeId(1L);
        trip.setPurpose("Meet");
        trip.setDestination("Hue");
        trip.setStartDate(START);
        trip.setEndDate(START.plusDays(4));
        when(tripRepo.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(trip));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createWithAdvanceAndDays() {
        assertCode(() -> trips.create(form(START.minusDays(1), null)), EEmployeeError.EMP_052);
        var t = trips.create(form(START.plusDays(4), new BigDecimal("5000000")));
        assertThat(t.status()).isEqualTo(EStatus.PLANNED);
        assertThat(t.days()).isEqualTo(5);
        assertThat(t.advance().status()).isEqualTo(EAdvanceStatus.REQUESTED);
        assertThat(trips.create(form(START, BigDecimal.ZERO)).advance()).isNull();
    }

    @Test
    void advanceAmountFixedOnceApproved() {
        trips.update(9L, form(START.plusDays(4), new BigDecimal("100")));
        assertCode(() -> trips.changeReimbursementStatus(9L, EReimbursementStatus.PAID), EEmployeeError.EMP_064);
        trips.changeAdvanceStatus(9L, EAdvanceStatus.APPROVED);
        assertCode(() -> trips.update(9L, form(START.plusDays(4), new BigDecimal("200"))), EEmployeeError.EMP_062);
        // same amount written differently is not a change
        trips.update(9L, form(START.plusDays(4), new BigDecimal("100.00")));
        // ...but dropping it is a change too
        assertCode(() -> trips.update(9L, form(START.plusDays(4), null)), EEmployeeError.EMP_062);
        assertThat(trip.getAdvanceAmount()).isEqualByComparingTo("100");
    }

    @Test
    void cancelledTripIsLocked() {
        trips.changeStatus(9L, EStatus.CANCELLED);
        assertCode(() -> trips.changeStatus(9L, EStatus.PLANNED), EEmployeeError.EMP_060);
        assertCode(() -> expenses.create(9L, line(START, "10")), EEmployeeError.EMP_060);
        trips.delete(9L);
        assertThat(trip.isDeleted()).isTrue();
    }

    @Test
    void editDeleteOnlyWhilePlannedAndMovesFollowTable() {
        trips.update(9L, form(START.plusDays(4), null));
        assertCode(() -> trips.changeStatus(9L, EStatus.COMPLETED), EEmployeeError.EMP_096);
        trips.changeStatus(9L, EStatus.ONGOING);
        assertCode(() -> trips.update(9L, form(START.plusDays(4), null)), EEmployeeError.EMP_102);
        assertCode(() -> trips.delete(9L), EEmployeeError.EMP_102);
        assertCode(() -> trips.changeStatus(9L, EStatus.PLANNED), EEmployeeError.EMP_096);
        trips.changeStatus(9L, EStatus.COMPLETED);
        assertCode(() -> trips.update(9L, form(START.plusDays(4), null)), EEmployeeError.EMP_102);
        assertCode(() -> trips.delete(9L), EEmployeeError.EMP_102);
        assertCode(() -> trips.changeStatus(9L, EStatus.CANCELLED), EEmployeeError.EMP_096);
        assertThat(trip.isDeleted()).isFalse();
        trip.setStatus(EStatus.PLANNED);
        trips.delete(9L);
        assertThat(trip.isDeleted()).isTrue();
    }

    @Test
    void expensesDriveTotalsAndSummary() {
        trip.setAdvanceAmount(new BigDecimal("300"));
        trip.setAdvanceStatus(EAdvanceStatus.PAID);
        assertCode(() -> expenses.create(9L, line(START.minusDays(1), "10")), EEmployeeError.EMP_065);
        when(expenseRepo.total(9L)).thenReturn(new BigDecimal("500"));
        when(expenseRepo.countByTripIdAndDeletedFalse(9L)).thenReturn(1L);
        var page = expenses.create(9L, line(START.plusDays(1), "500"));
        assertThat(page.expenses().reimbursementStatus()).isEqualTo(EReimbursementStatus.PENDING);
        assertThat(page.financialSummary().netBalance()).isEqualByComparingTo("200");
        assertThat(page.financialSummary().settlement()).isEqualByComparingTo("200");
        assertThat(page.financialSummary().progress()).isZero();
        trip.setAdvanceStatus(EAdvanceStatus.RECONCILED);
        assertThat(TripService.summary(trip).progress()).isEqualTo(50);
        trips.changeReimbursementStatus(9L, EReimbursementStatus.PAID);
        assertThat(TripService.summary(trip).settled()).isTrue();
        assertCode(() -> expenses.create(9L, line(START, "1")), EEmployeeError.EMP_063);
    }

    @Test
    void expenseTypeInUseCannotBeDeleted() {
        when(expenseRepo.existsByExpenseTypeIdAndDeletedFalse(4L)).thenReturn(true);
        assertCode(() -> catalogs.delete(ELifecycleCatalogKind.EXPENSE_TYPE, 4L), EEmployeeError.EMP_010);
    }

    private static TripForm form(LocalDate end, BigDecimal advance) {
        return new TripForm(1L, " Client meeting ", " Da Nang ", START, end, null, null, null, advance);
    }

    private static ExpenseForm line(LocalDate day, String amount) {
        return new ExpenseForm(4L, day, "Taxi", new BigDecimal(amount), null);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, EEmployeeError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
