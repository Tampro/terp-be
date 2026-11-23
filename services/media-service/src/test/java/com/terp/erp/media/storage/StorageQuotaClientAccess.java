package com.terp.erp.media.storage;

import java.math.BigDecimal;
import java.util.function.Function;

/** Lets tests in other packages build a StorageQuotaClient with the IAM call replaced. */
public final class StorageQuotaClientAccess {
    private StorageQuotaClientAccess() {
    }

    public static StorageQuotaClient of(Function<Long, BigDecimal> iam) {
        return new StorageQuotaClient(iam);
    }
}
