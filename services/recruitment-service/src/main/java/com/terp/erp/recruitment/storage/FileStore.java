package com.terp.erp.recruitment.storage;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import com.terp.erp.exception.ApiException;
import com.terp.erp.recruitment.common.ERecruitmentError;
import com.terp.erp.recruitment.config.RecruitmentProperties;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Resumes and cover letters on the service's own volume (PO, 2026-11-19: the public career page uploads them without a
 * token, so they cannot go to media-service). Only PDF / DOC / DOCX up to 5 MB, and the first bytes must match the
 * extension (an HTML page renamed .pdf is refused). Files are stored under a random key, never under the client's name.
 */
@Component
public class FileStore {
    public static final long MAX_BYTES = 5L * 1024 * 1024;

    public record StoredFile(String key, String name, String mimeType, long size) {
    }

    private record Type(String mime, byte[] magic) {
    }

    private static final Map<String, Type> ALLOWED = Map.of(
            "pdf", new Type("application/pdf", new byte[]{0x25, 0x50, 0x44, 0x46}),
            "docx", new Type("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    new byte[]{0x50, 0x4B, 0x03, 0x04}),
            "doc", new Type("application/msword", new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0}));

    private final Path root;

    public FileStore(RecruitmentProperties props) {
        this.root = Path.of(props.storageDir()).toAbsolutePath().normalize();
    }

    public StoredFile store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest(ERecruitmentError.REC_019);
        }
        if (file.getSize() > MAX_BYTES) {
            throw ApiException.badRequest(ERecruitmentError.REC_020, "5 MB");
        }
        String name = cleanName(file.getOriginalFilename());
        Type type = ALLOWED.get(extension(name));
        if (type == null || !startsWith(head(file), type.magic())) {
            throw ApiException.badRequest(ERecruitmentError.REC_019);
        }
        String key = UUID.randomUUID().toString().replace("-", "");
        try (InputStream in = file.getInputStream()) {
            Files.createDirectories(root);
            Files.copy(in, path(key), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw ApiException.badRequest(ERecruitmentError.REC_022);
        }
        return new StoredFile(key, name, type.mime(), file.getSize());
    }

    /** The stored file of a key; the key is a server-made hex string, so it cannot leave the root. */
    public Path path(String key) {
        if (key == null || !key.matches("^[0-9a-f]{32}$")) {
            throw new IllegalArgumentException("bad key");
        }
        return root.resolve(key);
    }

    public void delete(String key) {
        try {
            Files.deleteIfExists(path(key));
        } catch (IOException ignored) {
            // The row no longer points to it; a leftover file is harmless.
        }
    }

    static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Last path segment only, no control characters, at most 255 chars ("C:\\x\\a.pdf" or "../a.pdf" → "a.pdf"). */
    static String cleanName(String original) {
        String n = original == null ? "" : original;
        n = n.substring(Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\')) + 1).strip().replaceAll("\\p{Cntrl}", "");
        if (n.length() > 255) {
            n = n.substring(n.length() - 255);
        }
        return n.isEmpty() ? "file" : n;
    }

    private static byte[] head(MultipartFile f) {
        try (InputStream in = f.getInputStream()) {
            return in.readNBytes(8);
        } catch (IOException e) {
            throw ApiException.badRequest(ERecruitmentError.REC_022);
        }
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        return data.length >= prefix.length && Arrays.equals(Arrays.copyOf(data, prefix.length), prefix);
    }
}
