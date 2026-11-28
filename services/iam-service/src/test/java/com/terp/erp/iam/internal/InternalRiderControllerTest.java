package com.terp.erp.iam.internal;

import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.constant.EIamError;
import com.terp.erp.iam.constant.EUserStatus;
import com.terp.erp.iam.model.Permission;
import com.terp.erp.iam.model.Role;
import com.terp.erp.iam.model.UserAccount;
import com.terp.erp.iam.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InternalRiderControllerTest {
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final InternalRiderController controller = new InternalRiderController(users);

    private static UserAccount user(long id, long company, String... permissions) {
        Role role = new Role();
        for (String code : permissions) {
            Permission p = new Permission();
            p.setCode(code);
            role.getPermissions().add(p);
        }
        UserAccount u = new UserAccount();
        u.setId(id);
        u.setCompanyId(company);
        u.setKeycloakId("sub-" + id);
        u.setFullName("Rider " + id);
        u.setStatus(EUserStatus.ACTIVE);
        u.setRoles(Set.of(role));
        return u;
    }

    @Test
    void aUserWithDeliveryRideCanRideOthersCannot() {
        when(users.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(user(1L, 10L, "DELIVERY_RIDE")));
        when(users.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(user(2L, 10L, "ORDER_VIEW")));
        InternalRiderController.RiderDto r = controller.rider(10L, 1L).getData();
        assertThat(r.canRide()).isTrue();
        assertThat(r.active()).isTrue();
        assertThat(r.keycloakSub()).isEqualTo("sub-1");
        assertThat(controller.rider(10L, 2L).getData().canRide()).isFalse();
    }

    @Test
    void anotherCompanysUserIsNotFound() {
        when(users.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(user(1L, 11L, "DELIVERY_RIDE")));
        assertThatThrownBy(() -> controller.rider(10L, 1L))
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(EIamError.IAM_009));
    }
}
