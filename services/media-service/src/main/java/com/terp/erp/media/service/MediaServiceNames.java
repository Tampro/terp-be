package com.terp.erp.media.service;

/** Exposes the file-name cleaning rule of {@link MediaService} for tests in other packages. */
public final class MediaServiceNames {
    private MediaServiceNames() {
    }

    public static String clean(String original) {
        return MediaService.cleanName(original);
    }
}
