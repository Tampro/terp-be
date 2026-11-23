package com.terp.erp.crm.contract;

import com.terp.erp.exception.ApiException;

import java.util.Base64;
import java.util.Locale;

/**
 * Checks a hand-drawn signature sent as {@code data:image/png;base64,...} from the PNG header only (signature, IHDR
 * width/height); the image is never decoded, so a crafted body costs no more than the base64 decode.
 */
final class SignaturePng {
    static final int MAX_BYTES = 200 * 1024;
    static final int MAX_WIDTH = 2000;
    static final int MAX_HEIGHT = 1000;
    private static final String PREFIX = "data:image/png;base64,";
    private static final byte[] MAGIC = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private SignaturePng() {
    }

    static byte[] decode(String dataUrl) {
        if (dataUrl == null || dataUrl.length() < PREFIX.length()
                || !dataUrl.substring(0, PREFIX.length()).toLowerCase(Locale.ROOT).equals(PREFIX)) {
            throw ApiException.badRequest(EContractError.CTR_013);
        }
        String b64 = dataUrl.substring(PREFIX.length()).strip();
        // Refuse before decoding: 4 base64 chars carry 3 bytes.
        if ((long) b64.length() * 3 / 4 > MAX_BYTES + 3L) {
            throw ApiException.badRequest(EContractError.CTR_014, MAX_BYTES / 1024);
        }
        byte[] png;
        try {
            png = Base64.getDecoder().decode(b64);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(EContractError.CTR_013);
        }
        if (png.length > MAX_BYTES) {
            throw ApiException.badRequest(EContractError.CTR_014, MAX_BYTES / 1024);
        }
        // 8-byte signature, then the IHDR chunk: length 13, type "IHDR", width, height (big-endian).
        if (png.length < 33) {
            throw ApiException.badRequest(EContractError.CTR_013);
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (png[i] != MAGIC[i]) {
                throw ApiException.badRequest(EContractError.CTR_013);
            }
        }
        if (readInt(png, 8) != 13 || png[12] != 'I' || png[13] != 'H' || png[14] != 'D' || png[15] != 'R') {
            throw ApiException.badRequest(EContractError.CTR_013);
        }
        long width = readInt(png, 16) & 0xFFFFFFFFL;
        long height = readInt(png, 20) & 0xFFFFFFFFL;
        if (width < 1 || height < 1 || width > MAX_WIDTH || height > MAX_HEIGHT) {
            throw ApiException.badRequest(EContractError.CTR_015, MAX_WIDTH, MAX_HEIGHT);
        }
        return png;
    }

    private static int readInt(byte[] b, int at) {
        return ((b[at] & 0xFF) << 24) | ((b[at + 1] & 0xFF) << 16) | ((b[at + 2] & 0xFF) << 8) | (b[at + 3] & 0xFF);
    }
}
