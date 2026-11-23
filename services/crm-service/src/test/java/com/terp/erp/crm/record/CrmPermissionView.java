package com.terp.erp.crm.record;

import java.util.List;

/** Exposes the package-private CRM controllers to tests of other packages. */
public final class CrmPermissionView {
    private CrmPermissionView() {
    }

    /** Controllers whose handlers accept CRM_VIEW_OWN / CRM_MANAGE_OWN. */
    public static List<Class<?>> ownScoped() {
        return List.of(CrmControllers.LeadController.class, CrmControllers.DealController.class,
                CrmControllers.ActivityController.class);
    }
}
