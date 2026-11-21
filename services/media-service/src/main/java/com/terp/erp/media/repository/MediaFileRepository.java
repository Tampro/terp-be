package com.terp.erp.media.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import com.terp.erp.media.constant.EMediaKind;
import com.terp.erp.media.model.MediaFile;

import java.util.List;

public interface MediaFileRepository extends JpaRepository<MediaFile, Long> {

    /** Owner rule: allCompanies (super admin view) | platform files (companyId null) | one company. */
    String OWNER = "(:allCompanies = true or (:companyId is null and m.companyId is null) or m.companyId = :companyId)";

    @Query("select m from MediaFile m where " + OWNER
            + " and (:folderId is null or m.folderId = :folderId)"
            + " and (:kind is null or m.kind = :kind)"
            + " and (:keyword = '' or lower(m.displayName) like lower(concat('%', :keyword, '%'))"
            + "      or lower(m.originalName) like lower(concat('%', :keyword, '%')))")
    Page<MediaFile> search(boolean allCompanies, Long companyId, Long folderId, EMediaKind kind, String keyword,
                           Pageable pageable);

    /** Summary cards in one query: [file count, total bytes, image count]. */
    @Query("select count(m), coalesce(sum(m.sizeBytes), 0), "
            + "coalesce(sum(case when m.kind = com.terp.erp.media.constant.EMediaKind.IMAGE then 1 else 0 end), 0) "
            + "from MediaFile m where " + OWNER)
    List<Object[]> stats(boolean allCompanies, Long companyId);

    /** File count per folder for the sidebar: rows of [folderId, count]. */
    @Query("select m.folderId, count(m) from MediaFile m where m.folderId is not null and " + OWNER
            + " group by m.folderId")
    List<Object[]> countByFolder(boolean allCompanies, Long companyId);

    /** Deleting a folder keeps its files: they move to "no folder". */
    @Modifying
    @Query("update MediaFile m set m.folderId = null where m.folderId = :folderId")
    int detachFromFolder(Long folderId);
}
