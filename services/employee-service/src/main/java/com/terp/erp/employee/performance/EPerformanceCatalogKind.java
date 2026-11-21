package com.terp.erp.employee.performance;

import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.exception.ApiException;

import java.util.Arrays;

/** The company-managed performance lists and the path segment of each (docs/analysis/06 §7.3.5, §7.3.6). */
public enum EPerformanceCatalogKind {
    GOAL_TYPE("goal-types", "goal type"),
    INDICATOR_CATEGORY("indicator-categories", "indicator category");

    /** Regex for the {kind} path variable, so the other /performance pages are not caught by it. */
    static final String PATHS = "goal-types|indicator-categories";

    private final String path;
    private final String label;

    EPerformanceCatalogKind(String path, String label) {
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

    static EPerformanceCatalogKind ofPath(String path) {
        return Arrays.stream(values()).filter(k -> k.path.equals(path)).findFirst()
                .orElseThrow(() -> ApiException.notFound(EEmployeeError.EMP_071));
    }
}
