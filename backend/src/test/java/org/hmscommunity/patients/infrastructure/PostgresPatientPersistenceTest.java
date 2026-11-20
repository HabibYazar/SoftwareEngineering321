package org.hmscommunity.patients.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.hmscommunity.testsupport.ApiTestSupport.bearer;
import static org.hmscommunity.testsupport.ApiTestSupport.json;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.hmscommunity.common.persistence.PostgresErrors;
import org.hmscommunity.common.web.FieldViolation;
import org.hmscommunity.common.web.RequestValidationException;
import org.hmscommunity.patients.application.DuplicateNationalIdException;
import org.hmscommunity.patients.application.PatientRepository;
import org.hmscommunity.patients.application.PatientService;
import org.hmscommunity.patients.domain.ConditionLevel;
import org.hmscommunity.patients.domain.Patient;
import org.hmscommunity.patients.domain.PatientDetails;
import org.hmscommunity.patients.domain.RelativeInformation;
import org.hmscommunity.security.AuthenticatedStaff;
import org.hmscommunity.security.StaffRole;
import org.hmscommunity.testsupport.PostgresIntegrationTest;
import org.hmscommunity.testsupport.RequiresPostgres;
import org.hmscommunity.testsupport.SyntheticPatients;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * US-06/07/08 ve US-03: hasta kayıt, arama ve güncellemenin gerçek PostgreSQL ile uçtan uca doğrulanması.
 * Veritabanı satırları ve tetikleyicilerin yazdığı denetim kayıtları doğrudan okunur.
 */
@RequiresPostgres
class PostgresPatientPersistenceTest extends PostgresIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Locale TURKISH = Locale.forLanguageTag("tr-TR");

    @Autowired PatientRepository patients;
    @Autowired PatientService patientService;

    private String receptionistToken;
    private AuthenticatedStaff receptionist;

    @BeforeEach
    void signIn() throws Exception {
        receptionistToken = login(StaffRole.RECEPTIONIST);
        receptionist = accessTokens.resolve(receptionistToken).orElseThrow().staff();
    }

    // --- US-06: kayıt ---

    @Test
    void registrationPersistsPatientAndRelativeAndTheTriggerAuditsItOnce() throws Exception {
        String nationalId = SyntheticPatients.uniqueNationalId();
        Map<String, Object> body = SyntheticPatients.newPatientJson(nationalId);
        body.put("conditionLevel", "ADVANCED");
        body.put("relativeInformation", SyntheticPatients.relativeJson());

        UUID patientId = id(register(body).andExpect(status().isCreated())
            .andExpect(jsonPath("$.relativeInformation.fullName").value("Deneme Yakın")));

        Map<String, Object> row = database().admin().sql("""
                SELECT p.national_id, p.first_name, p.condition_level, p.date_of_birth, r.full_name, r.relationship, r.phone
                FROM patients p JOIN patient_relatives r ON r.patient_id = p.id WHERE p.id = ?
                """).param(patientId).query().singleRow();
        assertThat(row).containsEntry("national_id", nationalId).containsEntry("first_name", "Deneme")
            .containsEntry("condition_level", "ADVANCED").containsEntry("full_name", "Deneme Yakın")
            .containsEntry("relationship", "Sibling").containsEntry("phone", "0555 000 00 99");

        List<AuditRow> creates = auditEventsSinceTestStart("RECORD_CREATE");
        assertThat(creates).extracting(AuditRow::entityType).containsExactly("PATIENT", "PATIENT_RELATIVE");
        assertThat(creates).allSatisfy(event -> {
            assertThat(event.entityId()).isEqualTo(patientId);
            assertThat(event.actorId()).isEqualTo(receptionist.accountId());
            assertThat(event.sessionId()).isEqualTo(receptionist.sessionId());
            assertThat(event.actorRole()).isEqualTo("Receptionist");
            // Oluşturmada yalnızca alan adları kaydedilir, değerler kaydedilmez.
            assertThat(event.details().toString()).doesNotContain(nationalId).doesNotContain("Deneme");
        });
        assertThat(creates.getFirst().details().get("fields").toString()).contains("national_id", "phone");
    }

    @Test
    void duplicateNationalIdIsRejectedWith409() throws Exception {
        String nationalId = SyntheticPatients.uniqueNationalId();
        register(SyntheticPatients.newPatientJson(nationalId)).andExpect(status().isCreated());

        register(SyntheticPatients.newPatientJson(nationalId))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DUPLICATE_NATIONAL_ID"));
        assertThat(countPatients(nationalId)).isEqualTo(1);
    }

    @Test
    void concurrentRegistrationsOfOneNationalIdCreateExactlyOnePatientAndNoOrphanAudit() throws Exception {
        String nationalId = SyntheticPatients.uniqueNationalId();
        int attempts = 6;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            tasks.add(() -> {
                start.await();
                try {
                    patientService.register(nationalId, SyntheticPatients.mildDetails(), receptionist);
                    return true;
                } catch (DuplicateNationalIdException duplicate) {
                    return false;
                }
            });
        }
        List<Future<Boolean>> results = tasks.stream().map(pool::submit).toList();
        start.countDown();
        long successes = 0;
        for (Future<Boolean> result : results) {
            successes += result.get() ? 1 : 0;
        }
        pool.shutdown();

        assertThat(successes).isEqualTo(1);
        assertThat(countPatients(nationalId)).isEqualTo(1);
        // Geri alınan denemelerin denetim kayıtları da geri alınmış olmalıdır.
        assertThat(auditEventsSinceTestStart("RECORD_CREATE")).singleElement()
            .satisfies(event -> assertThat(event.entityType()).isEqualTo("PATIENT"));
    }

    @Test
    void advancedConditionWithoutRelativeIsRejectedAndNothingIsStored() throws Exception {
        String nationalId = SyntheticPatients.uniqueNationalId();
        Map<String, Object> body = SyntheticPatients.newPatientJson(nationalId);
        body.put("conditionLevel", "ADVANCED");

        register(body)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("relativeInformation"))
            .andExpect(jsonPath("$.errors[0].code").value("RequiredForAdvancedCondition"));
        assertThat(countPatients(nationalId)).isZero();
    }

    /** Java doğrulaması atlansa bile veritabanının ertelenmiş kuralı devreye girer ve işlem bütünüyle geri alınır. */
    @Test
    void databaseRuleViolationRollsBackThePatientRowAndItsAuditEvent() {
        Patient advancedWithoutRelative = Patient.create(UUID.randomUUID(), SyntheticPatients.uniqueNationalId(),
            withRelative(SyntheticPatients.mildDetails(), ConditionLevel.ADVANCED, null));

        assertThatThrownBy(() -> patients.insert(advancedWithoutRelative, receptionist))
            .isInstanceOfSatisfying(DataAccessException.class, failure ->
                assertThat(PostgresErrors.translate(failure)).hasValueSatisfying(api ->
                    assertThat(((RequestValidationException) api).violations())
                        .extracting(FieldViolation::field, FieldViolation::code)
                        .containsExactly(tuple("relativeInformation", "RELATIVE_REQUIRED"))));

        assertThat(patients.findById(advancedWithoutRelative.id())).isEmpty();
        assertThat(auditEventsSinceTestStart("RECORD_CREATE")).isEmpty();
    }

    @Test
    void databaseFieldErrorsAreTranslatedToFieldLevel400() {
        PatientDetails tooOld = new PatientDetails("Deneme", "Eski", LocalDate.of(1899, 12, 31), "Female",
            "0555 000 00 01", "Sentetik Adres", ConditionLevel.MILD, null);
        Patient patient = Patient.create(UUID.randomUUID(), SyntheticPatients.uniqueNationalId(), tooOld);

        assertThatThrownBy(() -> patients.insert(patient, receptionist))
            .isInstanceOfSatisfying(DataAccessException.class, failure ->
                assertThat(PostgresErrors.translate(failure)).hasValueSatisfying(api ->
                    assertThat(((RequestValidationException) api).violations()).singleElement().satisfies(violation -> {
                        assertThat(violation.field()).isEqualTo("dateOfBirth");
                        assertThat(violation.code()).isEqualTo("OUT_OF_RANGE");
                        assertThat(violation.message()).doesNotContain("1899");
                    })));
    }

    // --- US-07: arama ---

    @Test
    void exactNationalIdSearchAndViewAuditing() throws Exception {
        String nationalId = SyntheticPatients.uniqueNationalId();
        UUID patientId = id(register(SyntheticPatients.newPatientJson(nationalId)));
        UUID otherId = id(register(SyntheticPatients.newPatientJson(SyntheticPatients.uniqueNationalId())));

        mvc.perform(asReceptionist(get("/api/v1/patients").param("nationalId", nationalId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(patientId.toString()));
        mvc.perform(asReceptionist(get("/api/v1/patients/" + otherId))).andExpect(status().isOk());

        assertThat(auditEventsSinceTestStart("RECORD_VIEW")).satisfiesExactly(
            searchResult -> {
                assertThat(searchResult.entityId()).isEqualTo(patientId);
                assertThat(searchResult.details().get("accessChannel").asText()).isEqualTo("SEARCH_RESULT");
                assertThat(searchResult.actorId()).isEqualTo(receptionist.accountId());
                assertThat(searchResult.sessionId()).isEqualTo(receptionist.sessionId());
                assertThat(searchResult.actorRole()).isEqualTo("Receptionist");
            },
            recordView -> {
                assertThat(recordView.entityId()).isEqualTo(otherId);
                assertThat(recordView.details().get("accessChannel").asText()).isEqualTo("RECORD");
            });
    }

    @Test
    void partialNameSearchFollowsTurkishCaseRulesAndCollationOrder() throws Exception {
        String token = SyntheticPatients.uniqueToken();
        UUID cebeci = id(register(named("İpek", "Cebeci" + token)));
        UUID celik = id(register(named("Işıl", "Çelik" + token)));
        UUID ozan = id(register(named("Irmak", "Ozan" + token)));
        UUID ozturk = id(register(named("Ali", "Öztürk" + token)));

        assertThat(searchIds(token)).containsExactly(cebeci, celik, ozan, ozturk);
        // İ ↔ i ve I ↔ ı Türkçe kurallarıyla eşleşir.
        assertThat(searchIds(("İPEK CEBECİ" + token).toUpperCase(TURKISH))).containsExactly(cebeci);
        assertThat(searchIds("ışıl çelik" + token)).containsExactly(celik);
        assertThat(searchIds("IŞIL   ÇELİK" + token.toUpperCase(TURKISH))).containsExactly(celik);
        assertThat(searchIds("isil çelik" + token)).isEmpty();
        // LIKE joker karakterleri arama metninde özel anlam taşımaz.
        assertThat(searchIds("%" + token)).isEmpty();
    }

    @Test
    void searchWithoutMatchesReturnsAnEmptyArrayAndAuditsNothing() throws Exception {
        mvc.perform(asReceptionist(get("/api/v1/patients").param("name", "yok" + SyntheticPatients.uniqueToken())))
            .andExpect(status().isOk())
            .andExpect(content().json("[]"));
        mvc.perform(asReceptionist(get("/api/v1/patients").param("nationalId", SyntheticPatients.uniqueNationalId())))
            .andExpect(status().isOk())
            .andExpect(content().json("[]"));

        assertThat(auditEventsSinceTestStart("RECORD_VIEW")).isEmpty();
    }

    // --- US-08: güncelleme ---

    @Test
    void updateIsPersistedAndAuditedWithBeforeAndAfterValues() throws Exception {
        String nationalId = SyntheticPatients.uniqueNationalId();
        UUID patientId = id(register(SyntheticPatients.newPatientJson(nationalId)));
        long versionBefore = version(patientId);
        Map<String, Object> body = updateBody();
        body.put("phone", "0555 000 00 77");
        body.put("address", "Güncel Sentetik Adres No:9, Bursa");

        update(patientId, body)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.phone").value("0555 000 00 77"))
            .andExpect(jsonPath("$.nationalId").value(nationalId));

        assertThat(version(patientId)).isEqualTo(versionBefore + 1);
        assertThat(auditEventsSinceTestStart("RECORD_UPDATE")).singleElement().satisfies(event -> {
            assertThat(event.entityType()).isEqualTo("PATIENT");
            assertThat(event.entityId()).isEqualTo(patientId);
            assertThat(event.actorId()).isEqualTo(receptionist.accountId());
            assertThat(event.sessionId()).isEqualTo(receptionist.sessionId());
            JsonNode changes = event.details().get("changes");
            assertThat(changes.get("phone").get(0).asText()).isEqualTo("0555 000 00 01");
            assertThat(changes.get("phone").get(1).asText()).isEqualTo("0555 000 00 77");
            assertThat(changes.get("address").get(1).asText()).isEqualTo("Güncel Sentetik Adres No:9, Bursa");
            assertThat(changes.has("national_id")).isFalse();
            assertThat(changes.has("version")).isFalse();
        });
    }

    @Test
    void relativeInformationIsAddedOnUpgradeAndRemovedOnDowngrade() throws Exception {
        UUID patientId = id(register(SyntheticPatients.newPatientJson(SyntheticPatients.uniqueNationalId())));
        Map<String, Object> advanced = updateBody();
        advanced.put("conditionLevel", "ADVANCED");
        advanced.put("relativeInformation", SyntheticPatients.relativeJson());

        update(patientId, advanced).andExpect(status().isOk())
            .andExpect(jsonPath("$.relativeInformation.phone").value("0555 000 00 99"));
        assertThat(relativeCount(patientId)).isEqualTo(1);

        update(patientId, updateBody()).andExpect(status().isOk())
            .andExpect(jsonPath("$.relativeInformation").doesNotExist());
        assertThat(relativeCount(patientId)).isZero();

        List<AuditRow> events = auditEventsSinceTestStart();
        assertThat(events).extracting(AuditRow::action, AuditRow::entityType).contains(
            tuple("RECORD_UPDATE", "PATIENT"),
            tuple("RECORD_CREATE", "PATIENT_RELATIVE"),
            tuple("RECORD_DELETE", "PATIENT_RELATIVE"));
        assertThat(events.stream().filter(event -> event.action().equals("RECORD_UPDATE")).toList())
            .allSatisfy(event -> assertThat(event.details().get("changes").has("condition_level")).isTrue());
    }

    @Test
    void nationalIdCannotBeChangedEvenThoughTheDatabaseWouldLetAnAdministrator() throws Exception {
        String nationalId = SyntheticPatients.uniqueNationalId();
        UUID patientId = id(register(SyntheticPatients.newPatientJson(nationalId)));
        String adminToken = login(StaffRole.ADMINISTRATOR);
        Map<String, Object> body = updateBody();
        body.put("nationalId", SyntheticPatients.uniqueNationalId());

        mvc.perform(put("/api/v1/patients/" + patientId).header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content(json(body)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].code").value("Immutable"));

        assertThat(database().admin().sql("SELECT national_id FROM patients WHERE id = ?").param(patientId)
            .query(String.class).single()).isEqualTo(nationalId);
        assertThat(auditEventsSinceTestStart("RECORD_UPDATE")).isEmpty();
    }

    /** Hasta satırı güncellendikten sonra yakın bilgisi veritabanında reddedilirse her şey geri alınır. */
    @Test
    void failedRelativeWriteRollsBackThePatientUpdateAndItsAuditEvent() throws Exception {
        UUID patientId = id(register(SyntheticPatients.newPatientJson(SyntheticPatients.uniqueNationalId())));
        Patient current = patients.findById(patientId).orElseThrow();
        PatientDetails invalidRelative = withRelative(withPhone(current.details(), "0555 000 00 88"),
            ConditionLevel.ADVANCED, new RelativeInformation("Deneme Yakın", "Sibling", "x".repeat(40)));

        assertThatThrownBy(() -> patients.update(current.withDetails(invalidRelative), receptionist))
            .isInstanceOf(DataAccessException.class);

        assertThat(patients.findById(patientId)).contains(current);
        assertThat(auditEventsSinceTestStart("RECORD_UPDATE")).isEmpty();
        assertThat(relativeCount(patientId)).isZero();
    }

    @Test
    void unauthorizedRoleCannotReadOrChangePatientsInTheDatabase() throws Exception {
        UUID patientId = id(register(SyntheticPatients.newPatientJson(SyntheticPatients.uniqueNationalId())));
        String cleaningToken = login(StaffRole.CLEANING_STAFF);

        mvc.perform(get("/api/v1/patients/" + patientId).header(HttpHeaders.AUTHORIZATION, bearer(cleaningToken)))
            .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/patients/" + patientId).header(HttpHeaders.AUTHORIZATION, bearer(cleaningToken))
                .contentType(MediaType.APPLICATION_JSON).content(json(updateBody())))
            .andExpect(status().isForbidden());
        assertThat(auditEventsSinceTestStart()).extracting(AuditRow::action)
            .doesNotContain("RECORD_VIEW", "RECORD_UPDATE");
    }

    // --- yardımcılar ---

    private ResultActions register(Map<String, Object> body) throws Exception {
        return mvc.perform(post("/api/v1/patients").header(HttpHeaders.AUTHORIZATION, bearer(receptionistToken))
            .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    private ResultActions update(UUID patientId, Map<String, Object> body) throws Exception {
        return mvc.perform(put("/api/v1/patients/" + patientId).header(HttpHeaders.AUTHORIZATION, bearer(receptionistToken))
            .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    private MockHttpServletRequestBuilder asReceptionist(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, bearer(receptionistToken));
    }

    private List<UUID> searchIds(String name) throws Exception {
        String body = mvc.perform(asReceptionist(get("/api/v1/patients").param("name", name)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<UUID> ids = new ArrayList<>();
        JSON.readTree(body).forEach(patient -> ids.add(UUID.fromString(patient.get("id").asText())));
        return ids;
    }

    private static UUID id(ResultActions created) throws Exception {
        String body = created.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JSON.readTree(body).get("id").asText());
    }

    private static Map<String, Object> named(String firstName, String lastName) {
        Map<String, Object> body = SyntheticPatients.newPatientJson(SyntheticPatients.uniqueNationalId());
        body.put("firstName", firstName);
        body.put("lastName", lastName);
        return body;
    }

    private static Map<String, Object> updateBody() {
        Map<String, Object> body = SyntheticPatients.newPatientJson(null);
        body.remove("nationalId");
        return body;
    }

    private static PatientDetails withPhone(PatientDetails details, String phone) {
        return new PatientDetails(details.firstName(), details.lastName(), details.dateOfBirth(), details.gender(),
            phone, details.address(), details.conditionLevel(), details.relativeInformation());
    }

    private static PatientDetails withRelative(PatientDetails details, ConditionLevel level, RelativeInformation relative) {
        return new PatientDetails(details.firstName(), details.lastName(), details.dateOfBirth(), details.gender(),
            details.phone(), details.address(), level, relative);
    }

    private long countPatients(String nationalId) {
        return database().admin().sql("SELECT count(*) FROM patients WHERE national_id = ?").param(nationalId)
            .query(Long.class).single();
    }

    private long relativeCount(UUID patientId) {
        return database().admin().sql("SELECT count(*) FROM patient_relatives WHERE patient_id = ?").param(patientId)
            .query(Long.class).single();
    }

    private long version(UUID patientId) {
        return database().admin().sql("SELECT version FROM patients WHERE id = ?").param(patientId)
            .query(Long.class).single();
    }
}
