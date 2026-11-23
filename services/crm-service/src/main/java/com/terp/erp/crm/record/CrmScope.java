package com.terp.erp.crm.record;

import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.ErpPrincipal;
import com.terp.erp.security.OwnScope;

import java.util.Objects;

/**
 * "Own records only" scope of leads and deals (CRM_VIEW_OWN / CRM_MANAGE_OWN). A holder of the full code (or a super
 * admin) reaches every row of the company; anybody else, once the handler let them in with the _OWN code, only rows
 * they own. A full manager also sees every row: managing what you cannot see makes no sense.
 */
public final class CrmScope {
    public static final String VIEW = "CRM_VIEW";
    public static final String VIEW_OWN = "CRM_VIEW_OWN";
    public static final String MANAGE = "CRM_MANAGE";
    public static final String MANAGE_OWN = "CRM_MANAGE_OWN";

    private CrmScope() {
    }

    public static boolean ownView() {
        ErpPrincipal p = CurrentUser.get();
        return !(p.has(VIEW) || p.has(MANAGE));
    }

    public static boolean ownManage() {
        return !CurrentUser.get().has(MANAGE);
    }

    /** A lead is mine when I am its owner (assigned user) or I created it. */
    public static boolean mine(Lead l) {
        Long me = OwnScope.userId();
        return (me != null && me.equals(l.getOwnerUserId())) || Objects.equals(l.getCreatedBy(), OwnScope.sub());
    }
}
