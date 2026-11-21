package com.terp.erp.attendance.leave;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.attendance.leave.LeaveAppDtos.ApplicationDto;
import com.terp.erp.attendance.regularization.ERegularizationStatus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Export of the Leave Applications page (.xlsx, at most 5000 rows, same filters as the list). */
@Service
@RequiredArgsConstructor
public class LeaveExportService {
    static final int MAX_ROWS = 5000;

    private final LeaveApplicationService applications;

    @Transactional(readOnly = true)
    public byte[] export(Long requestedCompanyId, ERegularizationStatus status, Long employeeId, Long leaveTypeId,
                         Integer year, String keyword) {
        var rows = applications.search(requestedCompanyId, status, employeeId, leaveTypeId, year, keyword,
                PageRequest.of(0, MAX_ROWS, Sort.by("startDate"))).getContent();
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sh = wb.createSheet("Leaves");
            String[] head = {"Employee", "Email", "Leave Type", "Start Date", "End Date", "Days", "Status", "Reason",
                    "Applied On", "Comments"};
            Row h = sh.createRow(0);
            for (int i = 0; i < head.length; i++) {
                h.createCell(i).setCellValue(head[i]);
            }
            int r = 1;
            for (ApplicationDto a : rows) {
                Row x = sh.createRow(r++);
                x.createCell(0).setCellValue(nz(a.employee().fullName()));
                x.createCell(1).setCellValue(nz(a.employee().email()));
                x.createCell(2).setCellValue(a.leaveType() == null ? "" : a.leaveType().name());
                x.createCell(3).setCellValue(a.startDate().toString());
                x.createCell(4).setCellValue(a.endDate().toString());
                x.createCell(5).setCellValue(a.days());
                x.createCell(6).setCellValue(a.status().name());
                x.createCell(7).setCellValue(a.reason());
                x.createCell(8).setCellValue(a.appliedOn() == null ? "" : a.appliedOn().toString());
                x.createCell(9).setCellValue(nz(a.managerComments()));
            }
            sh.setColumnWidth(0, 26 * 256);
            sh.setColumnWidth(7, 40 * 256);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("export", e);
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
