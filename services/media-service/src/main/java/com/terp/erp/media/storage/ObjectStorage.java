package com.terp.erp.media.storage;

import java.io.InputStream;

/**
 * Bytes of media files under server-generated keys ({@code <owner>/<uuid>.<ext>}); a key never contains anything a
 * client sent. Implementations: {@link FileStorage} (local volume) and {@link S3FileStorage} (S3 / Wasabi / MinIO).
 */
public interface ObjectStorage {

    /** Stores the bytes; failures are {@code MEDIA_009}. */
    void put(String key, InputStream content, long size, String contentType);

    /** Opens the bytes for streaming; a missing object is {@code MEDIA_001}. The caller closes the stream. */
    InputStream get(String key);

    /** Best effort: a missing object is already the wanted outcome. */
    void delete(String key);

    boolean exists(String key);
}
