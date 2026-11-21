package com.terp.erp.attendance.biometric;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.attendance.biometric.BiometricDtos.DayCount;
import com.terp.erp.attendance.biometric.BiometricDtos.DayPage;
import com.terp.erp.attendance.biometric.BiometricDtos.DayRow;
import com.terp.erp.attendance.biometric.BiometricDtos.DeviceDto;
import com.terp.erp.attendance.biometric.BiometricDtos.DeviceForm;
import com.terp.erp.attendance.biometric.BiometricDtos.DeviceWithKey;
import com.terp.erp.attendance.biometric.BiometricDtos.EmployeeRef;
import com.terp.erp.attendance.biometric.BiometricDtos.PunchBatch;
import com.terp.erp.attendance.biometric.BiometricDtos.PunchIn;
import com.terp.erp.attendance.biometric.BiometricDtos.PunchLog;
import com.terp.erp.attendance.biometric.BiometricDtos.PunchResult;
import com.terp.erp.attendance.biometric.BiometricDtos.SyncForm;
import com.terp.erp.attendance.biometric.BiometricDtos.SyncResult;
import com.terp.erp.attendance.biometric.DeviceKeyFilter.DevicePrincipal;
import com.terp.erp.attendance.client.EmployeeClient;
import com.terp.erp.attendance.client.EmployeeClient.Employee;
import com.terp.erp.attendance.common.EAttendanceError;
import com.terp.erp.attendance.common.Texts;
import com.terp.erp.attendance.record.AttendanceRecord;
import com.terp.erp.attendance.record.AttendanceRecordRepository;
import com.terp.erp.attendance.record.AttendanceRecordService;
import com.terp.erp.attendance.record.RecordDtos.RecordForm;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Biometric Attendance page (docs/analysis/06 §6.4.3; PO, 2026-11-19): devices push punches with their key; the
 * admin browses them by day and Syncs a person's day into the attendance record (first punch = clock-in, last punch =
 * clock-out), overwriting the record's times. Device user ids are matched to employees by Employee Code, else
 * Employee No, when an admin looks (with the admin's token).
 * TODO(assumption): a punch belongs to the calendar date it was made on, so a night shift's punches after midnight
 * fall on the next day — confirm with BA.
 */
@Service
@RequiredArgsConstructor
public class BiometricService {
    static final int MAX_BATCH = 500;
    static final int MAX_RANGE_DAYS = 42;

    private final BiometricDeviceRepository devices;
    private final BiometricPunchRepository punches;
    private final AttendanceRecordService recordService;
    private final AttendanceRecordRepository records;
    private final EmployeeClient employees;
    private final Clock clock;

    // ------------------------------------------------------------------ devices

    @Transactional(readOnly = true)
    public Page<DeviceDto> devices(Long requestedCompanyId, Pageable pageable) {
        return devices.findByCompanyIdAndDeletedFalse(company(requestedCompanyId), pageable).map(DeviceDto::of);
    }

    @Transactional(readOnly = true)
    public DeviceDto device(Long id) {
        return DeviceDto.of(requireDevice(id));
    }

    @Transactional
    public DeviceWithKey createDevice(Long requestedCompanyId, DeviceForm form) {
        BiometricDevice d = new BiometricDevice();
        d.setCompanyId(company(requestedCompanyId));
        apply(d, form, -1L);
        String key = newKey(d);
        return new DeviceWithKey(DeviceDto.of(devices.save(d)), key);
    }

    /** Full replacement: omitted location is cleared, omitted status is kept. The key is unchanged. */
    @Transactional
    public DeviceDto updateDevice(Long id, DeviceForm form) {
        BiometricDevice d = requireDevice(id);
        apply(d, form, id);
        return DeviceDto.of(d);
    }

    /** A new key; the old one stops working at once. */
    @Transactional
    public DeviceWithKey rotateKey(Long id) {
        BiometricDevice d = requireDevice(id);
        String key = newKey(d);
        return new DeviceWithKey(DeviceDto.of(d), key);
    }

    /** Its key stops working; the punches it sent stay. */
    @Transactional
    public void deleteDevice(Long id) {
        requireDevice(id).setDeleted(true);
    }

    private void apply(BiometricDevice d, DeviceForm f, Long selfId) {
        String serial = f.serialNumber().trim();
        if (devices.serialTaken(d.getCompanyId(), serial, selfId)) {
            throw ApiException.badRequest(EAttendanceError.ATT_028);
        }
        d.setName(f.name().trim());
        d.setSerialNumber(serial);
        d.setLocation(Texts.blankToNull(f.location()));
        if (f.status() != null) {
            d.setStatus(f.status());
        }
    }

    private static String newKey(BiometricDevice d) {
        String key = DeviceKeys.generate();
        d.setApiKeyHash(DeviceKeys.hash(key));
        d.setKeyPrefix(DeviceKeys.prefix(key));
        return key;
    }

    // ------------------------------------------------------------------ device push

    /** Stores a device's punches; a punch already received (same device and external id) is skipped. */
    @Transactional
    public PunchResult receive(DevicePrincipal device, PunchBatch batch) {
        List<PunchIn> in = batch.punches();
        if (in.isEmpty() || in.size() > MAX_BATCH) {
            throw ApiException.badRequest(EAttendanceError.ATT_025, MAX_BATCH);
        }
        Map<String, PunchIn> byExternal = new LinkedHashMap<>();
        for (PunchIn p : in) {
            byExternal.putIfAbsent(externalId(p), p);
        }
        Set<String> known = new HashSet<>(punches.existingExternalIds(device.deviceId(), byExternal.keySet()));
        Instant now = clock.instant();
        List<BiometricPunch> fresh = new ArrayList<>();
        byExternal.forEach((ext, p) -> {
            if (known.contains(ext)) {
                return;
            }
            BiometricPunch b = new BiometricPunch();
            b.setCompanyId(device.companyId());
            b.setDeviceId(device.deviceId());
            b.setEmployeeCode(p.employeeCode().trim());
            b.setPunchedAt(p.punchedAt().truncatedTo(ChronoUnit.SECONDS));
            b.setExternalId(ext);
            b.setReceivedAt(now);
            fresh.add(b);
        });
        punches.saveAll(fresh);
        devices.findById(device.deviceId()).ifPresent(d -> d.setLastSeenAt(now));
        return new PunchResult(in.size(), fresh.size(), in.size() - fresh.size());
    }

    static String externalId(PunchIn p) {
        String e = Texts.blankToNull(p.externalId());
        return e != null ? e : p.employeeCode().trim() + "@" + p.punchedAt().truncatedTo(ChronoUnit.SECONDS);
    }

    // ------------------------------------------------------------------ daily view

    @Transactional(readOnly = true)
    public DayPage day(Long requestedCompanyId, LocalDate date, String keyword, Long employeeId, int page, int size) {
        Long companyId = company(requestedCompanyId);
        Map<String, Employee> people = byCode(employees.all(requestedCompanyId));
        Map<String, List<BiometricPunch>> byCode = new TreeMap<>();
        for (BiometricPunch p : punches.between(companyId, date.atStartOfDay(), date.plusDays(1).atStartOfDay())) {
            byCode.computeIfAbsent(p.getEmployeeCode().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(p);
        }
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<DayRow> rows = new ArrayList<>();
        for (List<BiometricPunch> ps : byCode.values()) {
            String code = ps.get(0).getEmployeeCode();
            Employee e = people.get(code.toLowerCase(Locale.ROOT));
            if (employeeId != null && (e == null || !e.id().equals(employeeId.toString()))) {
                continue;
            }
            if (!kw.isEmpty() && !code.toLowerCase(Locale.ROOT).contains(kw)
                    && (e == null || e.fullName() == null || !e.fullName().toLowerCase(Locale.ROOT).contains(kw))) {
                continue;
            }
            rows.add(row(code, e, ps));
        }
        int from = Math.min(page * size, rows.size());
        int to = Math.min(from + size, rows.size());
        return new DayPage(date, byCode.size(), rows.subList(from, to), page, size, rows.size());
    }

    static DayRow row(String code, Employee e, List<BiometricPunch> ps) {
        LocalTime first = ps.get(0).getPunchedAt().toLocalTime();
        LocalTime last = ps.size() > 1 ? ps.get(ps.size() - 1).getPunchedAt().toLocalTime() : null;
        boolean synced = ps.stream().allMatch(p -> p.getSyncedAt() != null);
        Long recordId = ps.stream().map(BiometricPunch::getAttendanceRecordId).filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);
        EmployeeRef ref = e == null ? null : new EmployeeRef(e.id(), e.fullName(),
                e.employeeCode() != null ? e.employeeCode() : e.employeeNo(),
                e.designation() == null ? null : e.designation().name(), e.profileImageUrl());
        return new DayRow(code, ref, first, last, ps.size(), synced, recordId == null ? null : recordId.toString());
    }

    /** People with punches per day of [from, to] (the date navigator). */
    @Transactional(readOnly = true)
    public List<DayCount> dayCounts(Long requestedCompanyId, LocalDate from, LocalDate to) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
            throw ApiException.badRequest(com.terp.erp.exception.ECommonError.COMMON_001);
        }
        Long companyId = company(requestedCompanyId);
        Map<LocalDate, Set<String>> by = new TreeMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            by.put(d, new HashSet<>());
        }
        for (BiometricPunch p : punches.between(companyId, from.atStartOfDay(), to.plusDays(1).atStartOfDay())) {
            by.get(p.getPunchedAt().toLocalDate()).add(p.getEmployeeCode().toLowerCase(Locale.ROOT));
        }
        return by.entrySet().stream().map(en -> new DayCount(en.getKey(), en.getValue().size())).toList();
    }

    /** Full punch log of one person's day (View). */
    @Transactional(readOnly = true)
    public List<PunchLog> log(Long requestedCompanyId, LocalDate date, String employeeCode) {
        Long companyId = company(requestedCompanyId);
        List<BiometricPunch> ps = punches.ofCode(companyId, employeeCode.trim(), date.atStartOfDay(),
                date.plusDays(1).atStartOfDay());
        Map<Long, String> names = devices.findByCompanyIdAndIdIn(companyId,
                        ps.stream().map(BiometricPunch::getDeviceId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(BiometricDevice::getId, BiometricDevice::getName));
        return ps.stream().map(p -> new PunchLog(p.getId().toString(), p.getPunchedAt().toLocalTime(),
                p.getDeviceId().toString(), names.get(p.getDeviceId()), p.getSyncedAt() != null)).toList();
    }

    // ------------------------------------------------------------------ sync

    /** First punch = clock-in, last = clock-out (one punch = clock-in only); overwrites an existing record's times. */
    @Transactional
    public SyncResult sync(Long requestedCompanyId, SyncForm form) {
        Long companyId = company(requestedCompanyId);
        List<BiometricPunch> ps = punches.ofCode(companyId, form.employeeCode().trim(), form.date().atStartOfDay(),
                form.date().plusDays(1).atStartOfDay());
        if (ps.isEmpty()) {
            throw ApiException.badRequest(EAttendanceError.ATT_026);
        }
        Employee e = byCode(employees.all(requestedCompanyId)).get(form.employeeCode().trim().toLowerCase(Locale.ROOT));
        if (e == null) {
            throw ApiException.badRequest(EAttendanceError.ATT_027);
        }
        LocalTime in = ps.get(0).getPunchedAt().toLocalTime();
        LocalTime out = ps.size() > 1 ? ps.get(ps.size() - 1).getPunchedAt().toLocalTime() : null;
        AttendanceRecord existing = records.findByEmployeeIdAndWorkDate(Long.valueOf(e.id()), form.date())
                .filter(r -> r.getCompanyId().equals(companyId)).orElse(null);
        AttendanceRecord r = existing == null ? new AttendanceRecord() : existing;
        r.setBiometricId(ps.get(0).getEmployeeCode());
        RecordForm f = new RecordForm(Long.valueOf(e.id()), form.date(), in, out, r.isHoliday(), r.getNotes());
        AttendanceRecord saved = recordService.save(r, companyId, e, f);
        Instant now = clock.instant();
        for (BiometricPunch p : ps) {
            p.setSyncedAt(now);
            p.setAttendanceRecordId(saved.getId());
        }
        return new SyncResult(saved.getId().toString(), in, out, ps.size(), existing == null);
    }

    /** Device user id → employee: Employee Code, else Employee No (case-insensitive). */
    static Map<String, Employee> byCode(List<Employee> list) {
        Map<String, Employee> m = new HashMap<>();
        for (Employee e : list) {
            String code = e.employeeCode() != null && !e.employeeCode().isBlank() ? e.employeeCode() : e.employeeNo();
            if (code != null) {
                m.putIfAbsent(code.trim().toLowerCase(Locale.ROOT), e);
            }
        }
        return m;
    }

    private BiometricDevice requireDevice(Long id) {
        BiometricDevice d = devices.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EAttendanceError.ATT_023));
        TenantGuard.assertSameCompany(d.getCompanyId(), EAttendanceError.ATT_023);
        return d;
    }

    private static Long company(Long requested) {
        return TenantGuard.resolveCompany(requested, EAttendanceError.ATT_004);
    }
}
