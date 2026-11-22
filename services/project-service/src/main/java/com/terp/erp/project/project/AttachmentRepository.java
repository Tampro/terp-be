package com.terp.erp.project.project;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    List<Attachment> findByProjectIdOrderByIdAsc(Long projectId);

    boolean existsByProjectIdAndFileId(Long projectId, Long fileId);
}
