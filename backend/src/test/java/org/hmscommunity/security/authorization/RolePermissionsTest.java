package org.hmscommunity.security.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import org.hmscommunity.security.StaffRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class RolePermissionsTest {

    @ParameterizedTest
    @EnumSource(Permission.class)
    void everyPermissionHasAnExplicitGrant(Permission permission) {
        assertThat(RolePermissions.rolesFor(permission)).isNotNull();
    }

    // Onaylı matris gelene kadar yalnızca kabul kriterlerinde açıkça geçen "receptionist" yetkilidir.
    @ParameterizedTest
    @EnumSource(Permission.class)
    void patientFunctionsAreGrantedOnlyToReceptionistUntilMatrixIsApproved(Permission permission) {
        assertThat(RolePermissions.rolesFor(permission)).containsExactly(StaffRole.RECEPTIONIST);
        assertThat(RolePermissions.authoritiesFor(permission)).containsExactly("ROLE_RECEPTIONIST");
    }

    @Test
    void otherRolesAreDeniedByDefault() {
        for (StaffRole role : StaffRole.values()) {
            for (Permission permission : Permission.values()) {
                assertThat(RolePermissions.isGranted(role, permission)).isEqualTo(role == StaffRole.RECEPTIONIST);
            }
        }
    }
}
