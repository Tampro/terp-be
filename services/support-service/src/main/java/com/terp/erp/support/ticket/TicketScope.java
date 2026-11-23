package com.terp.erp.support.ticket;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.ErpPrincipal;
import com.terp.erp.security.OwnScope;
import com.terp.erp.support.common.Texts;

import java.util.Objects;

/**
 * "Own tickets only" (SUPPORT_TICKET_VIEW_OWN / _MANAGE_OWN, and the client portal): a ticket I created
 * (created_by = my Keycloak sub) or whose requester email is the email of my account (token claim {@code email}),
 * so a client also finds the tickets they raised on the public portal before having an account.
 * TODO(assumption): the email claim is trusted without email_verified; IAM sets user emails, users cannot.
 * A full SUPPORT_TICKET_MANAGE holder also sees every ticket.
 */
public final class TicketScope {
    public static final String VIEW = "SUPPORT_TICKET_VIEW";
    public static final String MANAGE = "SUPPORT_TICKET_MANAGE";

    private TicketScope() {
    }

    public static boolean ownView() {
        ErpPrincipal p = CurrentUser.get();
        return !(p.has(VIEW) || p.has(MANAGE));
    }

    public static boolean ownManage() {
        return !CurrentUser.get().has(MANAGE);
    }

    /** Lower-cased email claim of the caller's token, null when the token has none. */
    public static String myEmail() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt) {
            return Texts.email(Texts.blankToNull(jwt.getToken().getClaimAsString("email")));
        }
        return null;
    }

    /** Display name for a ticket raised by the caller: token {@code name}, else the username. */
    public static String myName() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt) {
            String name = Texts.blankToNull(jwt.getToken().getClaimAsString("name"));
            if (name != null) {
                return name;
            }
        }
        return CurrentUser.get().username();
    }

    public static boolean mine(Ticket t) {
        String email = myEmail();
        return Objects.equals(t.getCreatedBy(), OwnScope.sub()) || (email != null && email.equals(t.getEmail()));
    }
}
