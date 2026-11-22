package com.terp.erp.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terp.erp.exception.ApiException;
import com.terp.erp.exception.RestExceptionHandler;
import com.terp.erp.support.common.ESupportError;
import com.terp.erp.support.contact.ContactMessage;
import com.terp.erp.support.contact.ContactMessageRepository;
import com.terp.erp.support.contact.ContactService;
import com.terp.erp.support.kb.Faq;
import com.terp.erp.support.kb.FaqRepository;
import com.terp.erp.support.kb.FaqService;
import com.terp.erp.support.kb.KbArticle;
import com.terp.erp.support.kb.KbArticleRepository;
import com.terp.erp.support.kb.KbCategory;
import com.terp.erp.support.kb.KbCategoryRepository;
import com.terp.erp.support.kb.KnowledgeService;
import com.terp.erp.support.portal.PortalPage;
import com.terp.erp.support.portal.PortalPageRepository;
import com.terp.erp.support.portal.PortalSettingsRepository;
import com.terp.erp.support.portal.PortalSettingsService;
import com.terp.erp.support.ticket.TicketCategory;
import com.terp.erp.support.ticket.TicketCategoryRepository;
import com.terp.erp.support.ticket.TicketCategoryService;
import com.terp.erp.support.ticket.TicketRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A row of company 20 is invisible to a user of company 10 on every by-id endpoint: 404 with the module's code. */
class TenantIsolationTest {
    private final KbCategoryRepository kbCategories = mock(KbCategoryRepository.class);
    private final KbArticleRepository articles = mock(KbArticleRepository.class);
    private final FaqRepository faqs = mock(FaqRepository.class);
    private final ContactMessageRepository contacts = mock(ContactMessageRepository.class);
    private final PortalPageRepository pages = mock(PortalPageRepository.class);
    private final TicketCategoryRepository ticketCategories = mock(TicketCategoryRepository.class);
    private final RestExceptionHandler messages = mock(RestExceptionHandler.class);

    private final KnowledgeService kb = new KnowledgeService(kbCategories, articles, messages);
    private final FaqService faqService = new FaqService(faqs, messages);
    private final ContactService contactService = new ContactService(contacts);
    private final PortalSettingsService portal = new PortalSettingsService(mock(PortalSettingsRepository.class), pages,
            new ObjectMapper());
    private final TicketCategoryService categoryService = new TicketCategoryService(ticketCategories,
            mock(TicketRepository.class));

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static void assertNotFound(Runnable call, ESupportError code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getErrorCode()).isEqualTo(code);
            assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        });
    }

    @Test
    void anotherCompanysRowsAnswer404() {
        TestLogin.as(10L, "SUPPORT_KB_VIEW", "SUPPORT_KB_MANAGE");
        KbArticle a = new KbArticle();
        a.setCompanyId(20L);
        a.setCategoryId(1L);
        when(articles.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(a));
        KbCategory kc = new KbCategory();
        kc.setCompanyId(20L);
        when(kbCategories.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(kc));
        Faq f = new Faq();
        f.setCompanyId(20L);
        when(faqs.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(f));
        ContactMessage m = new ContactMessage();
        m.setCompanyId(20L);
        when(contacts.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(m));
        PortalPage p = new PortalPage();
        p.setCompanyId(20L);
        when(pages.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(p));
        TicketCategory tc = new TicketCategory();
        tc.setCompanyId(20L);
        when(ticketCategories.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(tc));

        assertNotFound(() -> kb.get(1L), ESupportError.SUP_015);
        assertNotFound(() -> kb.delete(1L), ESupportError.SUP_015);
        assertNotFound(() -> kb.deleteCategory(1L), ESupportError.SUP_014);
        assertNotFound(() -> faqService.get(1L), ESupportError.SUP_016);
        assertNotFound(() -> faqService.delete(1L), ESupportError.SUP_016);
        assertNotFound(() -> contactService.get(1L), ESupportError.SUP_025);
        assertNotFound(() -> contactService.delete(1L), ESupportError.SUP_025);
        assertNotFound(() -> portal.page(1L), ESupportError.SUP_026);
        assertNotFound(() -> portal.deletePage(1L), ESupportError.SUP_026);
        assertNotFound(() -> categoryService.delete(1L), ESupportError.SUP_005);
        assertThat(m.isDeleted()).isFalse();
        assertThat(a.isDeleted()).isFalse();
    }

    @Test
    void aPublishedArticleOfAnotherCompanyIsNotOnThisPortal() {
        KbArticle a = new KbArticle();
        a.setCompanyId(20L);
        a.setPublished(true);
        when(articles.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(a));
        assertNotFound(() -> kb.getPublished(10L, 1L), ESupportError.SUP_015);
    }
}
