package com.terp.erp.commerce.portal;

import com.terp.erp.commerce.purchase.PurchaseInvoiceController;
import com.terp.erp.commerce.purchase.PurchaseReturnController;
import com.terp.erp.commerce.salesreturn.SalesReturnController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Every handler is guarded; portal handlers by PORTAL_*; no base VIEW/MANAGE code is checked without its _OWN. */
class PermissionAnnotationsTest {
    private static final Pattern HAS = Pattern.compile("@PermissionChecker\\.has\\('([A-Z_]+)'\\)");

    private static List<Method> handlers(Class<?> c) {
        List<Method> out = new ArrayList<>();
        for (Method m : c.getDeclaredMethods()) {
            if (m.isAnnotationPresent(GetMapping.class) || m.isAnnotationPresent(PostMapping.class)
                    || m.isAnnotationPresent(PutMapping.class) || m.isAnnotationPresent(DeleteMapping.class)) {
                out.add(m);
            }
        }
        return out;
    }

    @Test
    void portalHandlersRequireTheirPortalPermission() {
        List<Method> client = handlers(PortalControllers.ClientPortalController.class);
        assertThat(client).hasSize(16);
        for (Method m : client) {
            assertThat(m.getAnnotation(PreAuthorize.class)).as(m.getName()).isNotNull();
            assertThat(m.getAnnotation(PreAuthorize.class).value()).isEqualTo("@PermissionChecker.has('PORTAL_CLIENT')");
        }
        List<Method> vendor = handlers(PortalControllers.VendorPortalController.class);
        assertThat(vendor).hasSize(4);
        for (Method m : vendor) {
            assertThat(m.getAnnotation(PreAuthorize.class).value()).isEqualTo("@PermissionChecker.has('PORTAL_VENDOR')");
        }
    }

    @Test
    void documentHandlersAcceptTheOwnVariant() throws ClassNotFoundException {
        List<Class<?>> controllers = List.of(
                Class.forName("com.terp.erp.commerce.sales.SalesControllers$QuotationController"),
                Class.forName("com.terp.erp.commerce.sales.SalesControllers$ProposalController"),
                Class.forName("com.terp.erp.commerce.sales.SalesControllers$InvoiceController"),
                SalesReturnController.class, PurchaseInvoiceController.class, PurchaseReturnController.class);
        for (Class<?> c : controllers) {
            for (Method m : handlers(c)) {
                PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
                assertThat(pa).as(c.getSimpleName() + "." + m.getName()).isNotNull();
                Matcher single = HAS.matcher(pa.value());
                while (single.find()) {
                    // only codes without an OWN variant may still be checked alone
                    assertThat(single.group(1)).as(c.getSimpleName() + "." + m.getName())
                            .isIn("SALES_POST", "PURCHASE_POST");
                }
            }
        }
    }
}
