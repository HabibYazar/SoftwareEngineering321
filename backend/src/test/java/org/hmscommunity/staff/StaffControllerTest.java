package org.hmscommunity.staff;

import org.hmscommunity.security.SecurityProblemHandler;
import org.hmscommunity.security.authentication.AccessTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.hmscommunity.security.HttpSecurityConfiguration;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StaffController.class)
@Import(HttpSecurityConfiguration.class)
class StaffControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private StaffService staffService;

    @MockitoBean
    private AccessTokenService accessTokenService;

    @MockitoBean
    private SecurityProblemHandler securityProblemHandler;

    @Test
    void listCliniciansWithoutFilter() throws Exception {
        when(staffService.getDoctors(null)).thenReturn(List.of(
                new StaffRepository.Doctor(
                        UUID.fromString("21e2a9c9-04a7-51cd-bb50-8652e2278c37"),
                        "Doç. Dr. Ayşe ÖZDEMİR",
                        "Kardiyoloji"
                )
        ));

        mvc.perform(get("/api/v1/staff"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].displayName").value("Doç. Dr. Ayşe ÖZDEMİR"))
                .andExpect(jsonPath("$[0].department").value("Kardiyoloji"));
    }

    @Test
    void listCliniciansFilteredByDepartment() throws Exception {
        when(staffService.getDoctors("Kardiyoloji")).thenReturn(List.of(
                new StaffRepository.Doctor(
                        UUID.fromString("21e2a9c9-04a7-51cd-bb50-8652e2278c37"),
                        "Doç. Dr. Ayşe ÖZDEMİR",
                        "Kardiyoloji"
                )
        ));

        mvc.perform(get("/api/v1/staff?department=Kardiyoloji"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].displayName").value("Doç. Dr. Ayşe ÖZDEMİR"))
                .andExpect(jsonPath("$[0].department").value("Kardiyoloji"));
    }
}
