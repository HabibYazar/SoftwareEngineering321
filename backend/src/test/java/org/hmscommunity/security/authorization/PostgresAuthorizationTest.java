package org.hmscommunity.security.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hmscommunity.testsupport.ApiTestSupport.bearer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.hmscommunity.common.persistence.PostgresErrors;
import org.hmscommunity.patients.application.PatientRepository;
import org.hmscommunity.patients.domain.Patient;
import org.hmscommunity.security.AuthenticatedStaff;
import org.hmscommunity.security.StaffRole;
import org.hmscommunity.security.authorization.RoleBasedAccessIntegrationTest.ProtectedRoute;
import org.hmscommunity.testsupport.InMemoryStaffSessionStore;
import org.hmscommunity.testsupport.PostgresIntegrationTest;
import org.hmscommunity.testsupport.RequiresPostgres;
import org.hmscommunity.testsupport.SyntheticPatients;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

/**
 * US-02 (#132): HTTP yetkilendirmesi ile veritabanı izin modelinin tutarlılığı, gerçek PostgreSQL ile.
 *
 * <p>İzinler oturumla birlikte veritabanından ({@code has_permission}) okunduğu için iki katman ayrışamaz;
 * buradaki testler bunu yedi rolün tamamı, dinamik matris değişikliği ve veritabanının kendi izin
 * kontrolü (HTTP katmanı atlansa bile) üzerinden doğrular.
 */
@RequiresPostgres
class PostgresAuthorizationTest extends PostgresIntegrationTest {

    private static final String SOME_PATIENT = "/api/v1/patients/6f1c2b9e-3d4a-4f5b-8c6d-7e8f9a0b1c2d";

    private final Map<StaffRole, String> tokens = new EnumMap<>(StaffRole.class);

    @Autowired PatientRepository patients;

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("org.hmscommunity.security.authorization.RoleBasedAccessIntegrationTest#everyRoleOnEveryRoute")
    void databasePermissionsDecideEveryRoleOnEveryRoute(StaffRole role, ProtectedRoute route) throws Exception {
        int statusCode = mvc.perform(route.request.get().header(HttpHeaders.AUTHORIZATION, bearer(token(role))))
            .andReturn().getResponse().getStatus();

        // Veritabanısız testlerdeki beklentinin (başlangıç matrisi) aynısı gerçek veritabanıyla da sağlanmalıdır.
        if (InMemoryStaffSessionStore.INITIAL_DATABASE_MATRIX.getOrDefault(role, Set.of()).contains(route.permission)) {
            assertThat(statusCode).isNotIn(401, 403);
        } else {
            assertThat(statusCode).isEqualTo(403);
        }
    }

    @ParameterizedTest
    @EnumSource(ProtectedRoute.class)
    void anonymousRequestsAreRejectedWith401(ProtectedRoute route) throws Exception {
        mvc.perform(route.request.get())
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void javaPermissionsAndRolesExistInTheDatabaseCatalogue() {
        assertThat(database().admin().sql("SELECT code FROM permissions").query(String.class).list())
            .containsAll(Arrays.stream(Permission.values()).map(Permission::name).toList());
        assertThat(database().admin().sql("SELECT code FROM roles ORDER BY sort_order").query(String.class).list())
            .containsExactlyElementsOf(Arrays.stream(StaffRole.values()).map(StaffRole::apiName).toList());
    }

    /** US-05 matris değişikliği yeniden başlatma gerektirmeden, bir sonraki istekte etkili olur. */
    @Test
    void matrixChangesInTheDatabaseTakeEffectOnTheNextRequest() throws Exception {
        String nurse = token(StaffRole.NURSE);
        mvc.perform(get(SOME_PATIENT).header(HttpHeaders.AUTHORIZATION, bearer(nurse))).andExpect(status().isForbidden());

        database().asAdministrator("INSERT INTO role_permissions (role_id, permission_id) "
            + "SELECT r.id, p.id FROM roles r, permissions p WHERE r.code = ? AND p.code = ?", "Nurse", "PATIENT_VIEW");
        try {
            mvc.perform(get(SOME_PATIENT).header(HttpHeaders.AUTHORIZATION, bearer(nurse)))
                .andExpect(status().isNotFound());
        } finally {
            database().asAdministrator("DELETE FROM role_permissions WHERE role_id = (SELECT id FROM roles WHERE code = ?) "
                + "AND permission_id = (SELECT id FROM permissions WHERE code = ?)", "Nurse", "PATIENT_VIEW");
        }
        mvc.perform(get(SOME_PATIENT).header(HttpHeaders.AUTHORIZATION, bearer(nurse))).andExpect(status().isForbidden());
    }

    /** HTTP kuralı atlanmış olsa bile veritabanı yetkisiz yazmayı reddeder (HA403 → HTTP 403). */
    @Test
    void databaseRejectsAWriteThatBypassesTheHttpLayer() throws Exception {
        AuthenticatedStaff nurse = signedIn(StaffRole.NURSE);
        String nationalId = SyntheticPatients.uniqueNationalId();
        Patient patient = Patient.create(UUID.randomUUID(), nationalId, SyntheticPatients.mildDetails());

        assertThatThrownBy(() -> patients.insert(patient, nurse))
            .isInstanceOfSatisfying(DataAccessException.class, failure -> {
                assertThat(PostgresErrors.hasSqlState(failure, PostgresErrors.PERMISSION_DENIED)).isTrue();
                assertThat(PostgresErrors.translate(failure)).hasValueSatisfying(api ->
                    assertThat(api.status()).isEqualTo(HttpStatus.FORBIDDEN));
            });
        assertThat(patients.findByNationalId(nationalId)).isEmpty();
    }

    private String token(StaffRole role) throws Exception {
        String token = tokens.get(role);
        if (token == null) {
            token = login(role);
            tokens.put(role, token);
        }
        return token;
    }
}
