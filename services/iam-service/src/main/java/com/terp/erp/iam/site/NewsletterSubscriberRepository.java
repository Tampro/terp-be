package com.terp.erp.iam.site;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface NewsletterSubscriberRepository extends JpaRepository<NewsletterSubscriber, Long> {
    boolean existsByEmail(String email);

    java.util.Optional<NewsletterSubscriber> findByEmail(String email);

    java.util.Optional<NewsletterSubscriber> findByUnsubscribeToken(String unsubscribeToken);

    @Query("select n from NewsletterSubscriber n where :keyword = '' or lower(n.email) like lower(concat('%', :keyword, '%'))")
    Page<NewsletterSubscriber> search(String keyword, Pageable pageable);
}
