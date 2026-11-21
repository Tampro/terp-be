package com.terp.erp.media.storage;

import com.terp.erp.exception.ApiException;
import com.terp.erp.media.constant.EMediaError;
import com.terp.erp.media.constant.EMediaKind;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

/**
 * Which files may be uploaded, decided on the server: the extension must be on the allow-list and the first bytes
 * must match it. The client's Content-Type is ignored — it is whatever the client says. SVG and HTML are excluded on
 * purpose: they can carry script that runs when the file is opened from the media URL.
 */
public final class FileTypePolicy {

    public record FileType(String extension, String mimeType, EMediaKind kind, byte[][] magic) {
    }

    private static final byte[] ZIP = {0x50, 0x4B, 0x03, 0x04};
    private static final byte[] OLE = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0};

    private static final Map<String, FileType> ALLOWED = Map.ofEntries(
            type("png", "image/png", EMediaKind.IMAGE, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}),
            type("jpg", "image/jpeg", EMediaKind.IMAGE, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
            type("jpeg", "image/jpeg", EMediaKind.IMAGE, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
            type("gif", "image/gif", EMediaKind.IMAGE, new byte[]{0x47, 0x49, 0x46, 0x38}),
            type("webp", "image/webp", EMediaKind.IMAGE, new byte[]{0x52, 0x49, 0x46, 0x46}),
            type("pdf", "application/pdf", EMediaKind.DOCUMENT, new byte[]{0x25, 0x50, 0x44, 0x46}),
            type("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    EMediaKind.DOCUMENT, ZIP),
            type("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    EMediaKind.DOCUMENT, ZIP),
            type("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    EMediaKind.DOCUMENT, ZIP),
            type("doc", "application/msword", EMediaKind.DOCUMENT, OLE),
            type("xls", "application/vnd.ms-excel", EMediaKind.DOCUMENT, OLE),
            // Plain text has no signature; it is served with nosniff and as text/plain, so it cannot run.
            type("txt", "text/plain", EMediaKind.DOCUMENT),
            type("csv", "text/csv", EMediaKind.DOCUMENT));

    private FileTypePolicy() {
    }

    private static Map.Entry<String, FileType> type(String ext, String mime, EMediaKind kind, byte[]... magic) {
        return Map.entry(ext, new FileType(ext, mime, kind, magic));
    }

    /** Throws MEDIA_004 for a type not on the list, MEDIA_006 when the bytes don't match the extension. */
    public static FileType check(String originalName, byte[] head) {
        String ext = extensionOf(originalName);
        FileType t = ALLOWED.get(ext);
        if (t == null) {
            throw ApiException.badRequest(EMediaError.MEDIA_004, ext.isEmpty() ? "?" : ext);
        }
        if (t.magic().length > 0 && Arrays.stream(t.magic()).noneMatch(m -> startsWith(head, m))) {
            throw ApiException.badRequest(EMediaError.MEDIA_006);
        }
        return t;
    }

    static String extensionOf(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        return dot < 0 || dot == name.length() - 1 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
