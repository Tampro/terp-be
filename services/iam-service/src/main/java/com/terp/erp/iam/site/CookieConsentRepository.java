package com.terp.erp.iam.site;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface CookieConsentRepository extends JpaRepository<CookieConsent, Long> {
    List<CookieConsent> findByCreatedDateGreaterThanEqualAndCreatedDateLessThanOrderByCreatedDateAsc(Instant from, Instant to);
}
