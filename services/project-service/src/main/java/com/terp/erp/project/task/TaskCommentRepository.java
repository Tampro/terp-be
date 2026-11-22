package com.terp.erp.project.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaskCommentRepository extends JpaRepository<TaskComment, Long> {
    Optional<TaskComment> findByIdAndDeletedFalse(Long id);

    List<TaskComment> findByTaskIdAndDeletedFalseOrderByIdAsc(Long taskId);
}
