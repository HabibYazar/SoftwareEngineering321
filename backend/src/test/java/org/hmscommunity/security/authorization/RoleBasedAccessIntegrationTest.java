package org.hmscommunity.security.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hmscommunity.testsupport.ApiTestSupport.bearer;
import static org.hmscommunity.testsupport.ApiTestSupport.loginAs;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.hmscommunity.security.StaffRole;
import org.hmscommunity.testsupport.TestDoublesConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * US-02 (#132): yedi rolün tamamı için izinli ve yasak istekleri gerçek filtre zinciri üzerinden doğrular.
 * Burada yalnızca yetkilendirme kararı test edilir; uç noktaların iş davranışı kendi testlerindedir.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDoublesConfiguration.class)
class RoleBasedAccessIntegrationTest {

    private static final String SOME_PATIENT = "/api/v1/patients/6f1c2b9e-3d4a-4f5b-8c6d-7e8f9a0b1c2d";

    @Autowired MockMvc mvc;

    enum ProtectedRoute {
        REGISTER_PATIENT(Permission.PATIENT_REGISTER,
            () -> post("/api/v1/patients").contentType(MediaType.APPLICATION_JSON).content("{}")),
        SEARCH_PATIENTS(Permission.PATIENT_SEARCH, () -> get("/api/v1/patients").param("name", "test")),
        VIEW_PATIENT(Permission.PATIENT_VIEW, () -> get(SOME_PATIENT)),
        UPDATE_PATIENT(Permission.PATIENT_UPDATE,
            () -> put(SOME_PATIENT).contentType(MediaType.APPLICATION_JSON).content("{}"));

        final Permission permission;
        final Supplier<MockHttpServletRequestBuilder> request;

        ProtectedRoute(Permission permission, Supplier<MockHttpServletRequestBuilder> request) {
            this.permission = permission;
            this.request = request;
        }
    }

    static Stream<Arguments> everyRoleOnEveryRoute() {
        return Arrays.stream(StaffRole.values())
            .flatMap(role -> Arrays.stream(ProtectedRoute.values()).map(route -> Arguments.of(role, route)));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("everyRoleOnEveryRoute")
    void roleIsAllowedOrForbiddenAccordingToTheMatrix(StaffRole role, ProtectedRoute route) throws Exception {
        String token = loginAs(mvc, role);

        int statusCode = mvc.perform(route.request.get().header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andReturn().getResponse().getStatus();

        if (RolePermissions.isGranted(role, route.permission)) {
            // İzinli rol yetkilendirmeyi geçer; sonucu (200/201/400/404) iş kuralları belirler.
            assertThat(statusCode).isNotIn(401, 403);
        } else {
            assertThat(statusCode).isEqualTo(403);
        }
    }

    @ParameterizedTest
    @EnumSource(ProtectedRoute.class)
    void unauthenticatedRequestsAreRejectedWith401(ProtectedRoute route) throws Exception {
        mvc.perform(route.request.get())
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void forbiddenResponseUsesProblemFormat() throws Exception {
        String token = loginAs(mvc, StaffRole.CLEANING_STAFF);

        mvc.perform(get("/api/v1/patients").param("name", "test").header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
            .andExpect(jsonPath("$.status").value(403));
    }

    @ParameterizedTest
    @EnumSource(StaffRole.class)
    void undeclaredRoutesAreForbiddenForEveryAuthenticatedRole(StaffRole role) throws Exception {
        String token = loginAs(mvc, role);

        mvc.perform(get("/api/v1/patients/" + "6f1c2b9e-3d4a-4f5b-8c6d-7e8f9a0b1c2d/medical-history")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/not-a-route").header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @EnumSource(StaffRole.class)
    void everyRoleCanAuthenticateAndLogOut(StaffRole role) throws Exception {
        String token = loginAs(mvc, role);

        mvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isNoContent());
    }
}
