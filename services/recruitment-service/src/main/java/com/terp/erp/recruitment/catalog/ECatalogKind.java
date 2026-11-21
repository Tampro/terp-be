package com.terp.erp.recruitment.catalog;

import com.terp.erp.recruitment.common.ERecruitmentError;
import com.terp.erp.exception.ApiException;

import java.util.Arrays;

/** The four name / description / status catalogues of Recruitment, and the path segment of each. */
public enum ECatalogKind {
    JOB_CATEGORY("job-categories"),
    JOB_TYPE("job-types"),
    CANDIDATE_SOURCE("candidate-sources"),
    INTERVIEW_TYPE("interview-types");

    private final String path;

    ECatalogKind(String path) {
        this.path = path;
    }

    public String path() {
        return path;
    }

    static ECatalogKind ofPath(String path) {
        return Arrays.stream(values()).filter(k -> k.path.equals(path)).findFirst()
                .orElseThrow(() -> ApiException.notFound(ERecruitmentError.REC_001));
    }
}
