package com.terp.erp.media.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import com.terp.erp.exception.ApiException;
import com.terp.erp.media.constant.EMediaError;
import com.terp.erp.media.constant.EStorageDriver;

/**
 * Picks the storage of a driver. Writes use the driver the super admin chose (IAM Settings, Storage); reads and
 * deletes use the driver stored on the file row, so files written before a switch stay where they are. Never falls
 * back to local when S3 is chosen but not usable: the upload is refused instead.
 */
@Component
@RequiredArgsConstructor
public class StorageRouter {
    private final FileStorage local;
    private final S3FileStorage s3;

    /**
     * @param chosen the driver from IAM; null = IAM could not be read. Local is then used only when this server has
     *               no S3 configured (it cannot have been switched to S3); otherwise the upload waits (503 MEDIA_014).
     */
    public EStorageDriver writeDriver(EStorageDriver chosen) {
        EStorageDriver d = chosen;
        if (d == null) {
            if (s3.configured()) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, EMediaError.MEDIA_014);
            }
            d = EStorageDriver.LOCAL;
        }
        if (d.s3()) {
            s3.assertUsable(d);
        }
        return d;
    }

    public ObjectStorage of(EStorageDriver driver) {
        if (driver == null || driver == EStorageDriver.LOCAL) {
            return local;
        }
        if (!s3.configured()) {
            throw ApiException.badRequest(EMediaError.MEDIA_012, driver.name());
        }
        return s3;
    }

    public S3FileStorage s3() {
        return s3;
    }
}
