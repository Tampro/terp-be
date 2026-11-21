package com.terp.erp.iam.language;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface LanguageLabelRepository extends JpaRepository<LanguageLabel, Long> {
    List<LanguageLabel> findByLanguageId(Long languageId);

    @Modifying
    @Query("delete from LanguageLabel l where l.languageId = :languageId")
    void deleteByLanguage(Long languageId);
}
