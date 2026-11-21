package com.terp.erp.media.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.media.constant.EMediaKind;

/**
 * An uploaded file. The bytes live on the storage volume under {@code storageKey}, a server-generated path that
 * never contains anything the client sent. {@code companyId} null = a platform file (super admin).
 */
@Getter
@Setter
@Entity
@Table(name = "media_file")
public class MediaFile extends BaseAuditableEntity {
    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "folder_id")
    private Long folderId;

    /** As uploaded, only for display and the download file name. */
    @Column(name = "original_name", nullable = false)
    private String originalName;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    /** Decided by the server from the extension allow-list, not taken from the client's Content-Type. */
    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "extension", nullable = false, length = 10)
    private String extension;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private EMediaKind kind;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_key", nullable = false, length = 200)
    private String storageKey;
}
