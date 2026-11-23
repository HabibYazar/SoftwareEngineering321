package org.hmscommunity.staff;

import static org.hmscommunity.testsupport.ApiTestSupport.bearer;
import static org.hmscommunity.testsupport.ApiTestSupport.loginAs;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.hmscommunity.departments.DepartmentsRepository;
import org.hmscommunity.departments.DepartmentsRepository.Department;
import org.hmscommunity.security.StaffRole;
import org.hmscommunity.testsupport.TestDoublesConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * US-09 (#204) uç noktalarının gerçek güvenlik zinciri ve servis katmanıyla HTTP sözleşmesi.
 * Yalnızca veritabanı okuması dublörle değiştirilir; gerçek sorgular PostgresDoctorDirectoryTest içindedir.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDoublesConfiguration.class)
class StaffControllerTest {

    private static final StaffRepository.Clinician CARDIOLOGIST = new StaffRepository.Clinician(
        UUID.fromString("5e0d0000-0000-4000-8000-000000000201"), "Prof. Dr. Ayla Örnekoğlu", "Kardiyoloji");

    @Autowired MockMvc mvc;
    @MockitoBean StaffRepository staffRepository;
    @MockitoBean DepartmentsRepository departmentsRepository;

    @Test
    void receptionistListsDoctorsFilteredByDepartment() throws Exception {
        when(staffRepository.findClinicians("Kardiyoloji")).thenReturn(List.of(CARDIOLOGIST));

        mvc.perform(get("/api/v1/staff").param("department", "Kardiyoloji")
                .header(HttpHeaders.AUTHORIZATION, bearer(loginAs(mvc, StaffRole.RECEPTIONIST))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(CARDIOLOGIST.id().toString()))
            .andExpect(jsonPath("$[0].displayName").value("Prof. Dr. Ayla Örnekoğlu"))
            .andExpect(jsonPath("$[0].department").value("Kardiyoloji"));
    }

    @Test
    void blankFilterMeansAllDoctorsAndSurroundingWhitespaceIsIgnored() throws Exception {
        when(staffRepository.findClinicians(null)).thenReturn(List.of(CARDIOLOGIST));
        when(staffRepository.findClinicians("Kardiyoloji")).thenReturn(List.of(CARDIOLOGIST));
        String token = loginAs(mvc, StaffRole.RECEPTIONIST);

        mvc.perform(get("/api/v1/staff").param("department", " ").header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/staff").param("department", " Kardiyoloji ").header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void unknownDepartmentIsAnEmptyArrayNotAnError() throws Exception {
        when(staffRepository.findClinicians("Bulunmayan")).thenReturn(List.of());

        mvc.perform(get("/api/v1/staff").param("department", "Bulunmayan")
                .header(HttpHeaders.AUTHORIZATION, bearer(loginAs(mvc, StaffRole.RECEPTIONIST))))
            .andExpect(status().isOk())
            .andExpect(content().json("[]"));
    }

    @Test
    void overlongDepartmentFilterIsAFieldError() throws Exception {
        mvc.perform(get("/api/v1/staff").param("department", "a".repeat(101))
                .header(HttpHeaders.AUTHORIZATION, bearer(loginAs(mvc, StaffRole.RECEPTIONIST))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("department"));
        verify(staffRepository, never()).findClinicians(any());
    }

    @Test
    void departmentsUseTheContractFieldNames() throws Exception {
        UUID id = UUID.fromString("5e0d0000-0000-4000-8000-000000000108");
        when(departmentsRepository.findDepartments()).thenReturn(List.of(new Department(id, "Acil Tıp")));

        mvc.perform(get("/api/v1/departments").header(HttpHeaders.AUTHORIZATION, bearer(loginAs(mvc, StaffRole.RECEPTIONIST))))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[0].id").value(id.toString()))
            .andExpect(jsonPath("$[0].name").value("Acil Tıp"))
            .andExpect(jsonPath("$[0].department").doesNotExist());
    }

    @Test
    void directoryIsNotAnonymousAndIsForbiddenWithoutThePermission() throws Exception {
        for (String path : List.of("/api/v1/staff", "/api/v1/departments")) {
            mvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(loginAs(mvc, StaffRole.NURSE))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
        verify(staffRepository, never()).findClinicians(any());
        verify(departmentsRepository, never()).findDepartments();
    }

    /** Eski geçici uç nokta: servis sınıfı yanlışlıkla denetleyici olarak /departments yolunu açıyordu. */
    @Test
    void thereIsNoUnversionedDepartmentsEndpoint() throws Exception {
        mvc.perform(get("/departments").header(HttpHeaders.AUTHORIZATION, bearer(loginAs(mvc, StaffRole.RECEPTIONIST))))
            .andExpect(status().isForbidden());
        verify(departmentsRepository, never()).findDepartments();
    }
}
