package com.terp.erp.media.constant;

/**
 * Where a file's bytes live. Chosen by the super admin (IAM Settings → Storage → driver); stored on every file row so
 * a file stays readable from where it was written after the driver is switched. WASABI is S3 with a Wasabi endpoint.
 */
public enum EStorageDriver {
    LOCAL, S3, WASABI;

    public boolean s3() {
        return this != LOCAL;
    }
}
