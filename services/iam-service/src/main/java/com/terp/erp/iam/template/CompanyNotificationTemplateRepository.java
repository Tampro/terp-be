package com.terp.erp.iam.template;

import com.terp.erp.iam.template.CompanyNotificationTemplate.ETemplateChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyNotificationTemplateRepository extends JpaRepository<CompanyNotificationTemplate, Long> {
    List<CompanyNotificationTemplate> findByCompanyIdAndChannel(Long companyId, ETemplateChannel channel);

    List<CompanyNotificationTemplate> findByCompanyIdAndChannelAndTemplateKey(Long companyId, ETemplateChannel channel,
                                                                              String templateKey);

    Optional<CompanyNotificationTemplate> findByCompanyIdAndChannelAndTemplateKeyAndLang(
            Long companyId, ETemplateChannel channel, String templateKey, String lang);
}
