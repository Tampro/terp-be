package com.terp.erp.employee.lifecycle;

import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.exception.ApiException;

import java.util.Arrays;

/** The company-managed lists of the lifecycle pages and the path segment of each (PO, 2026-11-20). */
public enum ELifecycleCatalogKind {
    WARNING_TYPE("warning-types", "warning type"),
    WARNING_SEVERITY("warning-severities", "severity"),
    TERMINATION_TYPE("termination-types", "termination type"),
    COMPLAINT_TYPE("complaint-types", "complaint type"),
    RESOLUTION_TYPE("resolution-types", "resolution type"),
    EXPENSE_TYPE("expense-types", "expense type");

    private final String path;
    private final String label;

    ELifecycleCatalogKind(String path, String label) {
        this.path = path;
        this.label = label;
    }

    public String path() {
        return path;
    }

    /** Argument of EMP_054 ("The {0} is inactive..."). */
    public String label() {
        return label;
    }

    static ELifecycleCatalogKind ofPath(String path) {
        return Arrays.stream(values()).filter(k -> k.path.equals(path)).findFirst()
                .orElseThrow(() -> ApiException.notFound(EEmployeeError.EMP_050));
    }
}
