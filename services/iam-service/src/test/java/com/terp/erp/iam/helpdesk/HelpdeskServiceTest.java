package com.terp.erp.iam.helpdesk;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.helpdesk.HelpdeskDtos.ReplyForm;
import com.terp.erp.iam.helpdesk.HelpdeskDtos.TicketCreateForm;
import com.terp.erp.iam.helpdesk.HelpdeskDtos.TicketUpdateForm;
import com.terp.erp.iam.me.AvatarMediaClient;
import com.terp.erp.iam.model.Company;
import com.terp.erp.iam.repository.CompanyRepository;
import com.terp.erp.iam.repository.UserAccountRepository;
import com.terp.erp.security.ErpPrincipal;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HelpdeskServiceTest {
    private HelpdeskTicketRepository tickets;
    private HelpdeskReplyRepository replies;
    private HelpdeskCategoryRepository categories;
    private CompanyRepository companies;
    private AvatarMediaClient media;
    private HelpdeskService service;
    private com.terp.erp.outbox.OutboxPublisher outbox;
    private UserAccountRepository users;

    @BeforeEach
    void setUp() {
        tickets = mock(HelpdeskTicketRepository.class);
        replies = mock(HelpdeskReplyRepository.class);
        categories = mock(HelpdeskCategoryRepository.class);
        companies = mock(CompanyRepository.class);
        media = mock(AvatarMediaClient.class);
        outbox = mock(com.terp.erp.outbox.OutboxPublisher.class);
        users = mock(UserAccountRepository.class);
        service = new HelpdeskService(tickets, replies, categories, companies, users, media, outbox);
        when(tickets.save(any(HelpdeskTicket.class))).thenAnswer(inv -> {
            HelpdeskTicket t = inv.getArgument(0);
            t.setId(12L);
            return t;
        });
        when(replies.save(any(HelpdeskReply.class))).thenAnswer(inv -> {
            HelpdeskReply r = inv.getArgument(0);
            r.setId(90L);
            return r;
        });
        Company c = new Company();
        c.setId(10L);
        c.setName("Test Company");
        when(companies.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(c));
        when(companies.findAllById(any())).thenReturn(List.of(c));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ticketOfAnotherCompanyIsNotFound() {
        companyUser(10L);
        when(tickets.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(ticket(5L, 20L, EHelpdeskStatus.OPEN)));

        assertThatThrownBy(() -> service.get(5L))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    assertThat(((ApiException) e).getErrorCode()).isEqualTo(EHelpdeskError.HDK_001);
                    assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
        assertThatThrownBy(() -> service.reply(5L, new ReplyForm("<p>hi</p>", null, null)))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EHelpdeskError.HDK_001);
    }

    @Test
    void companyUserListsOnlyTheirOwnCompanyWhateverTheyAskFor() {
        companyUser(10L);
        Page<HelpdeskTicket> empty = new PageImpl<>(List.of());
        when(tickets.search(eq(10L), isNull(), isNull(), isNull(), isNull(), any())).thenReturn(empty);

        service.search(20L, null, null, null, null, PageRequest.of(0, 20));

        verify(tickets).search(eq(10L), isNull(), isNull(), isNull(), isNull(), any());
    }

    @Test
    void superAdminListsEveryCompanyWhenNoneIsNamed() {
        superAdmin();
        when(tickets.search(isNull(), isNull(), isNull(), isNull(), isNull(), any())).thenReturn(new PageImpl<>(List.of()));

        service.search(null, null, null, null, null, PageRequest.of(0, 20));

        verify(tickets).search(isNull(), isNull(), isNull(), isNull(), isNull(), any());
    }

    @Test
    void companyTicketStartsOpenAtMediumWithANumberAndMayNotSetPriority() {
        companyUser(10L);

        var created = service.create(new TicketCreateForm(99L, " Cannot log in ", "<p>x</p>", null, null));

        assertThat(created.ticket().companyId()).isEqualTo("10");
        assertThat(created.ticket().status()).isEqualTo(EHelpdeskStatus.OPEN);
        assertThat(created.ticket().priority()).isEqualTo(EHelpdeskPriority.MEDIUM);
        assertThat(created.ticket().ticketNumber()).isEqualTo("#00000012");
        assertThat(created.ticket().title()).isEqualTo("Cannot log in");
        assertThatThrownBy(() -> service.create(new TicketCreateForm(null, "T", "<p>x</p>", null,
                EHelpdeskPriority.URGENT)))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EHelpdeskError.HDK_009);
    }

    @Test
    void superAdminMustNameTheCompany() {
        superAdmin();
        assertThatThrownBy(() -> service.create(new TicketCreateForm(null, "T", "<p>x</p>", null, null)))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EHelpdeskError.HDK_008);
    }

    @Test
    void inactiveCategoryCannotBeChosen() {
        companyUser(10L);
        HelpdeskCategory c = new HelpdeskCategory();
        c.setId(3L);
        c.setActive(false);
        when(categories.findByIdAndDeletedFalse(3L)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.create(new TicketCreateForm(null, "T", "<p>x</p>", 3L, null)))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EHelpdeskError.HDK_003);
    }

    @Test
    void onlyThePlatformPostsInternalRepliesAndCompaniesNeverSeeThem() {
        companyUser(10L);
        when(tickets.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(ticket(5L, 10L, EHelpdeskStatus.OPEN)));

        assertThatThrownBy(() -> service.reply(5L, new ReplyForm("<p>note</p>", null, true)))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    assertThat(((ApiException) e).getErrorCode()).isEqualTo(EHelpdeskError.HDK_005);
                    assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });

        service.get(5L);
        verify(replies).findByTicketIdAndInternalFalseAndDeletedFalseOrderByIdAsc(5L);
        verify(replies, never()).findByTicketIdAndDeletedFalseOrderByIdAsc(anyLong());
    }

    @Test
    void closedTicketTakesNoReply() {
        superAdmin();
        when(tickets.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(ticket(5L, 10L, EHelpdeskStatus.CLOSED)));

        assertThatThrownBy(() -> service.reply(5L, new ReplyForm("<p>hi</p>", null, false)))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EHelpdeskError.HDK_004);
    }

    @Test
    void attachmentsAreCheckedAgainstTheTicketsCompany() {
        superAdmin();
        when(tickets.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(ticket(5L, 10L, EHelpdeskStatus.OPEN)));

        var r = service.reply(5L, new ReplyForm("<p>see file</p>", List.of(41L), false));

        verify(media).requireCompanyFile(eq(41L), eq(10L), eq(HelpdeskService.ATTACHMENT_TYPES),
                eq(HelpdeskService.ATTACHMENT_MAX_BYTES), eq(EHelpdeskError.HDK_007));
        assertThat(r.attachmentFileIds()).containsExactly("41");
        assertThat(r.fromPlatform()).isTrue();
    }

    @Test
    void resolvingStampsResolvedAtAndReopeningClearsIt() {
        superAdmin();
        HelpdeskTicket t = ticket(5L, 10L, EHelpdeskStatus.IN_PROGRESS);
        when(tickets.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(t));

        service.update(5L, new TicketUpdateForm("T", "<p>x</p>", null, EHelpdeskStatus.RESOLVED, EHelpdeskPriority.HIGH));
        assertThat(t.getResolvedAt()).isNotNull();
        assertThat(t.getPriority()).isEqualTo(EHelpdeskPriority.HIGH);

        service.update(5L, new TicketUpdateForm("T", "<p>x</p>", null, EHelpdeskStatus.OPEN, EHelpdeskPriority.HIGH));
        assertThat(t.getResolvedAt()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void aNewTicketIsPublishedForItsCreatorWithoutTheDescription() {
        companyUser(10L);

        service.create(new TicketCreateForm(null, "Cannot log in", "<p>secret details</p>", null, null));

        var payload = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(outbox).publish(eq(10L), eq("iam.helpdesk.ticket_created"), eq("HELPDESK_TICKET"), eq(12L),
                payload.capture());
        Map<String, Object> p = (Map<String, Object>) payload.getValue();
        assertThat(p).containsEntry("ticketNumber", "00000012").containsEntry("subject", "Cannot log in")
                .containsEntry("recipientUserIds", List.of("7"));
        assertThat(p.values()).doesNotContain("<p>secret details</p>");
    }

    @Test
    @SuppressWarnings("unchecked")
    void aPlatformReplyIsPublishedToTheTicketsCreatorAndAnInternalNoteIsNot() {
        superAdmin();
        HelpdeskTicket t = ticket(5L, 10L, EHelpdeskStatus.OPEN);
        t.setCreatedBy("kc-7");
        when(tickets.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(t));
        var creator = new com.terp.erp.iam.model.UserAccount();
        creator.setId(7L);
        creator.setCompanyId(10L);
        when(users.findByKeycloakIdAndDeletedFalse("kc-7")).thenReturn(Optional.of(creator));

        service.reply(5L, new ReplyForm("<p>internal</p>", null, true));
        verify(outbox, never()).publish(any(), any(), any(), any(), any());

        service.reply(5L, new ReplyForm("<p>hi</p>", null, false));
        var payload = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(outbox).publish(eq(10L), eq("iam.helpdesk.ticket_replied"), eq("HELPDESK_TICKET"), eq(5L),
                payload.capture());
        assertThat((Map<String, Object>) payload.getValue()).containsEntry("recipientUserIds", List.of("7"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aNewTicketIsAlsoAPlatformEventWithoutCompanyId() {
        companyUser(10L);
        Company c = new Company();
        c.setId(10L);
        c.setName("Test Company");
        when(companies.findById(10L)).thenReturn(Optional.of(c));

        service.create(new TicketCreateForm(null, "Cannot log in", "<p>secret details</p>", null, null));

        var payload = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(outbox).publish(org.mockito.ArgumentMatchers.isNull(), eq("platform.helpdesk.ticket_created"),
                eq("HELPDESK_TICKET"), eq(12L), payload.capture());
        Map<String, Object> p = (Map<String, Object>) payload.getValue();
        assertThat(p).containsEntry("companyId", "10").containsEntry("companyName", "Test Company")
                .doesNotContainKey("recipientUserIds");
        assertThat(p.values()).doesNotContain("<p>secret details</p>");
    }

    @Test
    void aCompanyReplyIsAPlatformEventAndAPlatformReplyIsNot() {
        HelpdeskTicket t = ticket(5L, 10L, EHelpdeskStatus.OPEN);
        when(tickets.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(t));
        companyUser(10L);
        service.reply(5L, new ReplyForm("<p>still broken</p>", null, false));
        verify(outbox).publish(org.mockito.ArgumentMatchers.isNull(), eq("platform.helpdesk.ticket_replied"),
                eq("HELPDESK_TICKET"), eq(5L), any());

        org.mockito.Mockito.clearInvocations(outbox);
        superAdmin();
        service.reply(5L, new ReplyForm("<p>fixed</p>", null, false));
        verify(outbox, never()).publish(org.mockito.ArgumentMatchers.isNull(), any(), any(), any(), any());
    }

    private static HelpdeskTicket ticket(Long id, Long companyId, EHelpdeskStatus status) {
        HelpdeskTicket t = new HelpdeskTicket();
        t.setId(id);
        t.setCompanyId(companyId);
        t.setTicketNumber(HelpdeskService.number(id));
        t.setTitle("T");
        t.setDescription("<p>x</p>");
        t.setStatus(status);
        t.setPriority(EHelpdeskPriority.MEDIUM);
        return t;
    }

    private static void companyUser(Long companyId) {
        login(new ErpPrincipal(7L, "kc-7", "user7", companyId, false, Set.of(),
                Set.of(HelpdeskPermissions.VIEW, HelpdeskPermissions.CREATE)));
    }

    private static void superAdmin() {
        login(new ErpPrincipal(null, "kc-root", "superadmin", null, true, Set.of(ErpPrincipal.SUPER_ADMIN_ROLE),
                Set.of()));
    }

    private static void login(ErpPrincipal p) {
        Jwt jwt = new Jwt("t", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"),
                Map.of("sub", p.keycloakId()));
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(), p.keycloakId());
        auth.setDetails(p);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
