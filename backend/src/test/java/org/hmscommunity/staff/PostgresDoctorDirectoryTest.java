package org.hmscommunity.staff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hmscommunity.testsupport.ApiTestSupport.bearer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.hmscommunity.security.StaffRole;
import org.hmscommunity.testsupport.PostgresIntegrationTest;
import org.hmscommunity.testsupport.PostgresTestDatabase;
import org.hmscommunity.testsupport.RequiresPostgres;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * US-09 (#201, #204): departman listesi ve departmana göre doktor dizini, gerçek PostgreSQL şeması
 * ({@code departments}, {@code doctor_departments}, {@code doctors} görünümü) ve sentetik seed ile.
 */
@RequiresPostgres
class PostgresDoctorDirectoryTest extends PostgresIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> SEEDED_DEPARTMENTS = List.of("Acil Tıp", "Çocuk Sağlığı ve Hastalıkları",
        "Genel Cerrahi", "Göz Hastalıkları", "İç Hastalıkları", "Kardiyoloji", "Nöroloji", "Ortopedi ve Travmatoloji");

    private String receptionist;

    @BeforeEach
    void signIn() throws Exception {
        receptionist = login(StaffRole.RECEPTIONIST);
    }

    @Test
    void allSeededDepartmentsAreListedFromTheDatabaseInTurkishOrder() throws Exception {
        JsonNode departments = getJson("/api/v1/departments");

        List<String> names = new ArrayList<>();
        departments.forEach(department -> {
            assertThat(department.fieldNames()).toIterable().containsExactlyInAnyOrder("id", "name");
            names.add(department.get("name").asText());
        });
        assertThat(names).containsExactlyElementsOf(SEEDED_DEPARTMENTS);
        assertThat((long) departments.size()).isEqualTo(database().admin()
            .sql("SELECT count(*) FROM departments WHERE is_active").query(Long.class).single());
    }

    @Test
    void selectingADepartmentReturnsOnlyItsDoctors() throws Exception {
        for (String department : SEEDED_DEPARTMENTS) {
            JsonNode doctors = getJson("/api/v1/staff?department=" + department);

            long expected = database().admin().sql("SELECT count(*) FROM doctors WHERE department = ?")
                .param(department).query(Long.class).single();
            assertThat(doctors.size()).as(department).isEqualTo(expected);
            doctors.forEach(doctor -> assertThat(doctor.get("department").asText()).isEqualTo(department));
        }
        assertThat(getJson("/api/v1/staff?department=Kardiyoloji")).extracting(node -> node.get("displayName").asText())
            .containsExactly("Uzm. Dr. Kerem Denemeci", "Prof. Dr. Ayla Örnekoğlu");
    }

    @Test
    void departmentWithoutDoctorsAndUnknownDepartmentReturnEmptyArrays() throws Exception {
        mvc.perform(get("/api/v1/staff").param("department", "Acil Tıp").header(HttpHeaders.AUTHORIZATION, bearer(receptionist)))
            .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/v1/staff").param("department", "Bulunmayan Departman")
                .header(HttpHeaders.AUTHORIZATION, bearer(receptionist)))
            .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void unfilteredListContainsEveryAssignedPhysicianOnce() throws Exception {
        JsonNode doctors = getJson("/api/v1/staff");

        assertThat((long) doctors.size()).isEqualTo(database().admin().sql("SELECT count(*) FROM doctors")
            .query(Long.class).single()).isGreaterThanOrEqualTo(14L);
        List<String> ids = new ArrayList<>();
        doctors.forEach(doctor -> ids.add(doctor.get("id").asText()));
        assertThat(ids).doesNotHaveDuplicates();
    }

    /** Pasif departman ve doktorları, veritabanının doctors görünümüyle aynı biçimde gizlenir. */
    @Test
    void inactiveDepartmentDisappearsFromBothLists() throws Exception {
        database().asAdministrator("UPDATE departments SET is_active = false WHERE name = ?", "Göz Hastalıkları");
        try {
            assertThat(getJson("/api/v1/departments")).extracting(node -> node.get("name").asText())
                .doesNotContain("Göz Hastalıkları");
            assertThat(getJson("/api/v1/staff?department=Göz Hastalıkları")).isEmpty();
        } finally {
            database().asAdministrator("UPDATE departments SET is_active = true WHERE name = ?", "Göz Hastalıkları");
        }
        assertThat(getJson("/api/v1/staff?department=Göz Hastalıkları")).hasSize(1);
    }

    @Test
    void directoryRequiresTheDatabasePermission() throws Exception {
        String nurse = login(StaffRole.NURSE);
        String administrator = login(StaffRole.ADMINISTRATOR);

        mvc.perform(get("/api/v1/departments")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/staff").header(HttpHeaders.AUTHORIZATION, bearer(nurse))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/staff").header(HttpHeaders.AUTHORIZATION, bearer(administrator))).andExpect(status().isOk());
    }

    /** Seed yeniden çalıştırılsa da yinelenen satır üretmez (NFR-03) ve Flyway geçmişini bozmaz. */
    @Test
    void seedIsIdempotentAndDoesNotDisturbTheMigrationHistory() throws Exception {
        long departments = count("departments");
        long doctors = count("doctor_departments");
        long staff = count("staff_members");

        try (Connection connection = DriverManager.getConnection(database().jdbcUrl(), database().adminUser(),
                database().adminPassword());
             InputStream script = getClass().getResourceAsStream("/db/seed/R__us09_synthetic_doctor_directory.sql");
             Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.execute(new String(script.readAllBytes(), StandardCharsets.UTF_8));
            connection.commit();
        }

        assertThat(count("departments")).isEqualTo(departments);
        assertThat(count("doctor_departments")).isEqualTo(doctors);
        assertThat(count("staff_members")).isEqualTo(staff);
        assertThat(database().migrate(database().jdbcUrl(), PostgresTestDatabase.MIGRATION_AND_SEED_LOCATIONS)).isZero();
        assertThat(database().admin().sql("SELECT count(*) FROM flyway_schema_history WHERE NOT success")
            .query(Long.class).single()).isZero();
    }

    private JsonNode getJson(String path) throws Exception {
        String body = mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(receptionist)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JSON.readTree(body);
    }

    private long count(String table) {
        return database().admin().sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }
}
