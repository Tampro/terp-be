package com.terp.erp.recruitment.question;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CustomQuestionRepository extends JpaRepository<CustomQuestion, Long> {
    /** keyword matches the question text; the caller escapes % and _ with !. */
    @Query("select q from CustomQuestion q where q.companyId = :companyId and q.deleted = false "
            + "and (:kw = '' or lower(q.question) like :kw escape '!')")
    Page<CustomQuestion> search(Long companyId, String kw, Pageable pageable);

    List<CustomQuestion> findByCompanyIdAndDeletedFalseOrderByIdAsc(Long companyId);

    Optional<CustomQuestion> findByIdAndDeletedFalse(Long id);
}
