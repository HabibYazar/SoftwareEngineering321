package org.hmscommunity.patients.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.hmscommunity.common.persistence.ConditionalOnDatabase;
import org.hmscommunity.common.persistence.DatabaseActorContext;
import org.hmscommunity.common.persistence.PostgresErrors;
import org.hmscommunity.patients.application.DuplicateNationalIdException;
import org.hmscommunity.patients.application.PatientNotFoundException;
import org.hmscommunity.patients.application.PatientRepository;
import org.hmscommunity.patients.domain.ConditionLevel;
import org.hmscommunity.patients.domain.Patient;
import org.hmscommunity.patients.domain.RelativeInformation;
import org.hmscommunity.security.AuthenticatedStaff;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * US-06/07/08: hastaları PostgreSQL'de saklar ({@code patients} ve 1:1 {@code patient_relatives}).
 *
 * <p>Veritabanı tarafında her yazmada izin kontrolü, alan doğrulaması ({@code HV001}) ve denetim kaydı
 * (oluşturmada alan adları, güncellemede önceki/sonraki değerler) tetikleyicilerle aynı işlemde çalışır.
 * "Advanced seviyede yakın zorunlu" kuralı ertelenmiş (deferred) bir kısıttır; hatanın COMMIT yerine bu
 * metot içinde alınması için yazmaların sonunda {@code SET CONSTRAINTS ALL IMMEDIATE} çalıştırılır.
 *
 * <p>Ad araması ve sıralama, veritabanının varsayılan yerel ayarından bağımsız olsun diye açıkça Türkçe ICU
 * harmanlamasıyla ({@code tr-TR-x-icu}) yapılır; sunucunun ICU desteğiyle derlenmiş olması gerekir.
 */
@Repository
@ConditionalOnDatabase
class JdbcPatientRepository implements PatientRepository {

    private static final String SELECT_PATIENT = """
        SELECT p.id, p.national_id, p.first_name, p.last_name, p.date_of_birth, p.gender, p.phone, p.address,
               p.condition_level, r.full_name AS relative_full_name, r.relationship AS relative_relationship,
               r.phone AS relative_phone
        FROM patients p
        LEFT JOIN patient_relatives r ON r.patient_id = p.id
        """;

    private final JdbcClient jdbc;
    private final DatabaseActorContext actorContext;

    JdbcPatientRepository(JdbcClient jdbc, DatabaseActorContext actorContext) {
        this.jdbc = jdbc;
        this.actorContext = actorContext;
    }

    @Override
    @Transactional
    public Patient insert(Patient patient, AuthenticatedStaff actor) {
        actorContext.bind(actor.accountId(), actor.sessionId());
        try {
            jdbc.sql("""
                    INSERT INTO patients (id, national_id, first_name, last_name, date_of_birth, gender, phone,
                                          address, condition_level)
                    VALUES (:id, :nationalId, :firstName, :lastName, :dateOfBirth, :gender, :phone,
                            :address, :conditionLevel)
                    """)
                .param("id", patient.id())
                .param("nationalId", patient.nationalId())
                .params(profileParameters(patient))
                .update();
            if (patient.relativeInformation() != null) {
                insertRelative(patient.id(), patient.relativeInformation());
            }
            checkDeferredConstraints();
        } catch (DataAccessException failure) {
            if (isDuplicateNationalId(failure)) {
                throw new DuplicateNationalIdException();
            }
            throw failure;
        }
        return findById(patient.id()).orElseThrow();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Patient> findById(UUID id) {
        return jdbc.sql(SELECT_PATIENT + "WHERE p.id = ?").param(id).query(JdbcPatientRepository::map).optional();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Patient> findByNationalId(String nationalId) {
        return jdbc.sql(SELECT_PATIENT + "WHERE p.national_id = ?").param(nationalId)
            .query(JdbcPatientRepository::map).optional();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Patient> searchByName(String nameFragment) {
        // strpos, LIKE joker karakterlerini (% ve _) yorumlamadığı için kullanıcı girdisinin kaçışlanması gerekmez.
        return jdbc.sql(SELECT_PATIENT + """
                WHERE strpos(lower((p.first_name || ' ' || p.last_name) COLLATE "tr-TR-x-icu"),
                             lower(CAST(? AS text) COLLATE "tr-TR-x-icu")) > 0
                ORDER BY p.last_name COLLATE "tr-TR-x-icu", p.first_name COLLATE "tr-TR-x-icu", p.id
                """)
            .param(nameFragment)
            .query(JdbcPatientRepository::map)
            .list();
    }

    /**
     * Önce hasta satırı güncellenir; bu satır kilidi aynı hasta üzerindeki eşzamanlı güncellemeleri sıraya
     * sokar, böylece yakın bilgisi değişiklikleri iç içe geçmez. {@code version} her güncellemede artırılır.
     */
    @Override
    @Transactional
    public Patient update(Patient patient, AuthenticatedStaff actor) {
        actorContext.bind(actor.accountId(), actor.sessionId());
        int updated = jdbc.sql("""
                UPDATE patients
                SET first_name = :firstName, last_name = :lastName, date_of_birth = :dateOfBirth, gender = :gender,
                    phone = :phone, address = :address, condition_level = :conditionLevel, version = version + 1
                WHERE id = :id
                """)
            .param("id", patient.id())
            .params(profileParameters(patient))
            .update();
        if (updated == 0) {
            throw new PatientNotFoundException();
        }
        RelativeInformation relative = patient.relativeInformation();
        if (relative == null) {
            jdbc.sql("DELETE FROM patient_relatives WHERE patient_id = ?").param(patient.id()).update();
        } else if (jdbc.sql("""
                UPDATE patient_relatives
                SET full_name = :fullName, relationship = :relationship, phone = :phone, version = version + 1
                WHERE patient_id = :patientId
                """)
            .param("patientId", patient.id())
            .params(relativeParameters(relative))
            .update() == 0) {
            insertRelative(patient.id(), relative);
        }
        checkDeferredConstraints();
        return findById(patient.id()).orElseThrow(PatientNotFoundException::new);
    }

    private void insertRelative(UUID patientId, RelativeInformation relative) {
        jdbc.sql("""
                INSERT INTO patient_relatives (patient_id, full_name, relationship, phone)
                VALUES (:patientId, :fullName, :relationship, :phone)
                """)
            .param("patientId", patientId)
            .params(relativeParameters(relative))
            .update();
    }

    private void checkDeferredConstraints() {
        jdbc.sql("SET CONSTRAINTS ALL IMMEDIATE").update();
    }

    private static Map<String, Object> profileParameters(Patient patient) {
        Map<String, Object> values = new HashMap<>();
        values.put("firstName", patient.firstName());
        values.put("lastName", patient.lastName());
        values.put("dateOfBirth", patient.dateOfBirth());
        values.put("gender", patient.gender());
        values.put("phone", patient.phone());
        values.put("address", patient.address());
        values.put("conditionLevel", patient.conditionLevel() == null ? null : patient.conditionLevel().name());
        return values;
    }

    private static Map<String, Object> relativeParameters(RelativeInformation relative) {
        return Map.of("fullName", relative.fullName(), "relationship", relative.relationship(),
            "phone", relative.phone());
    }

    private static boolean isDuplicateNationalId(DataAccessException failure) {
        Optional<ServerErrorMessage> error = PostgresErrors.serverError(failure);
        if (error.isEmpty()) {
            return false;
        }
        ServerErrorMessage message = error.get();
        if (PostgresErrors.UNIQUE_VIOLATION.equals(message.getSQLState())) {
            return "uq_patients_national_id".equals(message.getConstraint());
        }
        return PostgresErrors.VALIDATION_FAILED.equals(message.getSQLState())
            && PostgresErrors.fieldErrors(message).stream()
                .anyMatch(field -> "nationalId".equals(field.field()) && "DUPLICATE".equals(field.error()));
    }

    private static Patient map(ResultSet row, int rowNumber) throws SQLException {
        String relativeName = row.getString("relative_full_name");
        RelativeInformation relative = relativeName == null ? null : new RelativeInformation(relativeName,
            row.getString("relative_relationship"), row.getString("relative_phone"));
        return new Patient(
            row.getObject("id", UUID.class),
            row.getString("first_name"),
            row.getString("last_name"),
            row.getString("national_id"),
            row.getObject("date_of_birth", LocalDate.class),
            row.getString("gender"),
            row.getString("phone"),
            row.getString("address"),
            ConditionLevel.valueOf(row.getString("condition_level")),
            relative);
    }
}
