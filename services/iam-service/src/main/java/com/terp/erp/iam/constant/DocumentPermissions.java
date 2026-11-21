package com.terp.erp.iam.constant;

import java.util.Set;

/**
 * Codes checked by document-service for §9.4: DOCUMENT_* for categories, HR documents, acknowledgments and document
 * templates; CONTRACT_* for contract types, employee contracts and contract templates. Seeded in
 * 20261121120000_document_permission_init_data (PO, 2026-11-21).
 */
public final class DocumentPermissions {
    public static final Set<String> ALL = Set.of("DOCUMENT_VIEW", "DOCUMENT_MANAGE", "CONTRACT_VIEW",
            "CONTRACT_MANAGE");

    private DocumentPermissions() {
    }
}
