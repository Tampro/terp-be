package com.terp.erp.media.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * S3-compatible storage (AWS S3, Wasabi, MinIO), all from this service's own environment (MEDIA_S3_*). The
 * credentials never pass through IAM, the database or an API response: the super admin only picks the driver in IAM
 * Settings. Every field may be blank; then the S3 driver is "not configured", an upload with driver S3/WASABI is
 * refused (MEDIA_012), and the service still starts with the local driver working.
 *
 * @param endpoint         e.g. https://s3.ap-southeast-1.amazonaws.com, https://s3.wasabisys.com, http://seaweedfs:8333;
 *                         blank = the AWS endpoint of the region
 * @param pathStyle        true for MinIO (bucket in the path, not in the host name)
 * @param prefix           optional key prefix inside the bucket, e.g. "erp/"
 * @param createBucket     create the bucket when it is missing (local MinIO only)
 * @param allowedHttpHosts hosts that may be reached over plain http (MinIO inside the compose network); every other
 *                         endpoint must be https
 */
@ConfigurationProperties(prefix = "erp.media.s3")
public record S3Properties(
        String endpoint,
        String region,
        String bucket,
        String accessKey,
        String secretKey,
        boolean pathStyle,
        String prefix,
        boolean createBucket,
        List<String> allowedHttpHosts,
        Duration connectTimeout,
        Duration readTimeout) {

    public S3Properties {
        allowedHttpHosts = allowedHttpHosts == null ? List.of() : List.copyOf(allowedHttpHosts);
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(3) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(30) : readTimeout;
        prefix = prefix == null ? "" : prefix.strip();
    }

    public boolean configured() {
        return notBlank(bucket) && notBlank(accessKey) && notBlank(secretKey) && notBlank(region);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** A record prints every component: keep the credentials out of logs and error pages. */
    @Override
    public String toString() {
        return "S3Properties[endpoint=" + endpoint + ", region=" + region + ", bucket=" + bucket + ", pathStyle="
                + pathStyle + ", prefix=" + prefix + ", credentials=" + (configured() ? "set" : "unset") + "]";
    }
}
