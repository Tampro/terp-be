package com.terp.erp.media.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.terp.erp.exception.ApiException;
import com.terp.erp.media.config.MediaProperties;
import com.terp.erp.media.constant.EMediaError;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Files on the local storage volume. Keys are {@code <owner>/<uuid>.<ext>} built by the server only, and every
 * resolved path is checked to stay under the root — a key can never reach outside it.
 */
@Slf4j
@Component
public class FileStorage {
    private final Path root;

    public FileStorage(MediaProperties props) throws IOException {
        this.root = Path.of(props.storageDir()).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    /** @param owner "platform" or "c{companyId}" */
    public String save(String owner, String extension, InputStream content) {
        String key = owner + "/" + UUID.randomUUID() + "." + extension;
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
            return key;
        } catch (IOException e) {
            log.error("Could not store a file: {}", e.getClass().getSimpleName());
            throw ApiException.badRequest(EMediaError.MEDIA_009);
        }
    }

    public Path path(String key) {
        return resolve(key);
    }

    /** Best effort: a missing file is already the wanted outcome. */
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            log.warn("Could not delete stored file {}: {}", key, e.getClass().getSimpleName());
        }
    }

    private Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("Storage key outside the root");
        }
        return p;
    }
}
