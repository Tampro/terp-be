package com.terp.erp.employee.io;

import org.apache.poi.ss.usermodel.Row;
import com.terp.erp.employee.employee.EmployeeDtos.EmployeeCreateForm;
import com.terp.erp.employee.org.OrgService;

/** Test access to the package-private row parser. */
public final class ImportRowsAccess {
    private ImportRowsAccess() {
    }

    public static EmployeeCreateForm toForm(Row row, OrgService.Tree tree, int excelRow) {
        return EmployeeExcelService.toForm(row, tree, excelRow);
    }
}
