package com.terp.erp.media.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.terp.erp.media.model.MediaFolder;

import java.util.List;
import java.util.Optional;

public interface MediaFolderRepository extends JpaRepository<MediaFolder, Long> {
    Optional<MediaFolder> findByIdAndDeletedFalse(Long id);

    /** {@code allCompanies} = super admin's "everything" view; otherwise one owner ({@code companyId} null = platform). */
    @Query("select f from MediaFolder f where f.deleted = false and (:allCompanies = true"
            + " or (:companyId is null and f.companyId is null) or f.companyId = :companyId) order by f.name")
    List<MediaFolder> findVisible(boolean allCompanies, Long companyId);

    @Query("select count(f) > 0 from MediaFolder f where f.deleted = false and lower(f.name) = lower(:name)"
            + " and ((:companyId is null and f.companyId is null) or f.companyId = :companyId)")
    boolean existsByOwnerAndName(Long companyId, String name);
}
