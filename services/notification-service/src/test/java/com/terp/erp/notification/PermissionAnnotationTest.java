package com.terp.erp.notification;

import com.terp.erp.notification.delivery.DeliveryController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Every handler checks a permission seeded in IAM (20261123100000_notification_permission_init_data). */
class PermissionAnnotationTest {
    private static final Set<String> SEEDED = Set.of("NOTIFICATION_LOG_VIEW", "NOTIFICATION_LOG_MANAGE");
    private static final Pattern CODE = Pattern.compile("@PermissionChecker\\.has\\('([A-Z_]+)'\\)");

    @Test
    void everyHandlerHasASeededPermissionAndWritesNeedManage() {
        List<Method> hs = Arrays.stream(DeliveryController.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> Arrays.stream(m.getAnnotations()).anyMatch(a ->
                        a.annotationType().isAnnotationPresent(RequestMapping.class)))
                .toList();
        assertThat(hs).hasSize(3);
        for (Method m : hs) {
            PreAuthorize p = m.getAnnotation(PreAuthorize.class);
            assertThat(p).as(m.getName()).isNotNull();
            Matcher x = CODE.matcher(p.value());
            assertThat(x.matches()).as(p.value()).isTrue();
            assertThat(SEEDED).contains(x.group(1));
            assertThat(x.group(1).endsWith("_MANAGE")).as(m.getName())
                    .isEqualTo(!m.isAnnotationPresent(GetMapping.class));
        }
    }
}
