package com.terp.erp.attendance.biometric;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.terp.erp.attendance.common.EStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public final class BiometricDtos {
    private BiometricDtos() {
    }

    // ------------------------------------------------------------------ devices

    /** keyPrefix = first 8 characters of the key, to tell keys apart; the key itself is never returned again. */
    public record DeviceDto(String id, String name, String serialNumber, String location, EStatus status,
                            String keyPrefix, Instant lastSeenAt, Instant createdDate, Instant updatedDate) {
        static DeviceDto of(BiometricDevice d) {
            return new DeviceDto(d.getId().toString(), d.getName(), d.getSerialNumber(), d.getLocation(), d.getStatus(),
                    d.getKeyPrefix(), d.getLastSeenAt(), d.getCreatedDate(), d.getUpdatedDate());
        }
    }

    /** Returned by create and rotate-key only: copy apiKey into the device now, it is not shown again. */
    public record DeviceWithKey(DeviceDto device, String apiKey) {
    }

    public record DeviceForm(
            @Schema(example = "Front door reader", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 150) String name,
            @Schema(example = "SN-000123", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 100) String serialNumber,
            @Schema(example = "Head office, ground floor") @Size(max = 200) String location,
            @Schema(description = "Omitted on create = ACTIVE; omitted on update = unchanged") EStatus status) {
    }

    // ------------------------------------------------------------------ device push

    public record PunchIn(
            @Schema(description = "The device's user id = the employee's Employee Code, else Employee No",
                    example = "EMP000001", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 50) String employeeCode,
            @Schema(description = "Company wall-clock time, no offset", example = "2026-11-18T08:01:00",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDateTime punchedAt,
            @Schema(description = "Device log id: a re-sent punch with the same id is ignored; omitted = code + time",
                    example = "log-8812") @Size(max = 100) String externalId) {
    }

    public record PunchBatch(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull List<@Valid PunchIn> punches) {
    }

    public record PunchResult(int received, int stored, int duplicates) {
    }

    // ------------------------------------------------------------------ daily view

    public record EmployeeRef(String id, String fullName, String employeeCode, String designation,
                              String profileImageUrl) {
    }

    /**
     * One person's punches of the day. employee null = the device user id matches no employee (Unknown employee).
     * lastOut is null with a single punch. synced = every punch is in the attendance record.
     */
    public record DayRow(String employeeCode, EmployeeRef employee, LocalTime firstIn, LocalTime lastOut,
                         int punches, boolean synced, String attendanceRecordId) {
    }

    public record DayPage(LocalDate date, long totalRecords, List<DayRow> rows, int page, int size, long totalElements) {
    }

    public record DayCount(LocalDate date, long records) {
    }

    public record PunchLog(String id, LocalTime time, String deviceId, String deviceName, boolean synced) {
    }

    public record SyncForm(
            @Schema(example = "2026-11-18", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate date,
            @Schema(example = "EMP000001", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 50) String employeeCode) {
    }

    public record SyncResult(String attendanceRecordId, LocalTime clockIn, LocalTime clockOut, int punches,
                             boolean created) {
    }
}
