package com.terp.erp.iam.integration;

import com.terp.erp.iam.integration.CompanyChannelEvent.EChatChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyChannelEventRepository extends JpaRepository<CompanyChannelEvent, Long> {
    List<CompanyChannelEvent> findByCompanyIdAndChannel(Long companyId, EChatChannel channel);

    Optional<CompanyChannelEvent> findByCompanyIdAndChannelAndEventType(Long companyId, EChatChannel channel,
                                                                        String eventType);
}
