package com.terp.erp.iam.constant;

import java.util.Set;

/** Codes checked by employee-service for §7.4; seeded in 20261120420000_training_permission_init_data. */
public final class TrainingPermissions {
    public static final Set<String> ALL = Set.of("TRAINING_VIEW", "TRAINING_MANAGE");

    private TrainingPermissions() {
    }
}
