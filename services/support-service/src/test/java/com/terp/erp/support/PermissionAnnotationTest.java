package com.terp.erp.support;

import com.terp.erp.support.contact.ContactController;
import com.terp.erp.support.kb.KnowledgeController;
import com.terp.erp.support.messenger.MessengerController;
import com.terp.erp.support.portal.PortalSettingsController;
import com.terp.erp.support.portal.PublicSupportController;
import com.terp.erp.support.ticket.TicketController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Every handler of the token-protected controllers checks a seeded permission; the public portal checks none. */
class PermissionAnnotationTest {
    private static final Set<String> SEEDED = Set.of("SUPPORT_TICKET_VIEW", "SUPPORT_TICKET_MANAGE",
            "SUPPORT_KB_VIEW", "SUPPORT_KB_MANAGE", "SUPPORT_CONTACT_VIEW", "SUPPORT_CONTACT_MANAGE",
            "SUPPORT_SETTINGS_MANAGE", "MESSENGER_USE");
    private static final Pattern CODE = Pattern.compile("@PermissionChecker\\.has\\('([A-Z_]+)'\\)");

    private static List<Method> handlers(Class<?> c) {
        return Arrays.stream(c.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> Arrays.stream(m.getAnnotations()).anyMatch(a ->
                        a.annotationType().isAnnotationPresent(RequestMapping.class)))
                .toList();
    }

    @Test
    void everyProtectedHandlerHasASeededPermission() {
        for (Class<?> c : List.of(TicketController.class, KnowledgeController.class, ContactController.class,
                PortalSettingsController.class, MessengerController.class)) {
            List<Method> hs = handlers(c);
            assertThat(hs).as(c.getSimpleName()).isNotEmpty();
            for (Method m : hs) {
                PreAuthorize p = m.getAnnotation(PreAuthorize.class);
                assertThat(p).as(c.getSimpleName() + "." + m.getName()).isNotNull();
                Matcher x = CODE.matcher(p.value());
                assertThat(x.matches()).as(p.value()).isTrue();
                assertThat(SEEDED).as(c.getSimpleName() + "." + m.getName()).contains(x.group(1));
            }
        }
    }

    @Test
    void writesNeedAManagePermission() {
        for (Class<?> c : List.of(TicketController.class, KnowledgeController.class, ContactController.class)) {
            for (Method m : handlers(c)) {
                boolean read = m.isAnnotationPresent(org.springframework.web.bind.annotation.GetMapping.class);
                String code = m.getAnnotation(PreAuthorize.class).value();
                assertThat(code.contains("_MANAGE")).as(c.getSimpleName() + "." + m.getName()).isEqualTo(!read);
            }
        }
    }

    @Test
    void thePublicPortalHasNoPermissionCheck() {
        assertThat(handlers(PublicSupportController.class)).hasSize(9)
                .allSatisfy(m -> assertThat(m.getAnnotation(PreAuthorize.class)).isNull());
    }
}
