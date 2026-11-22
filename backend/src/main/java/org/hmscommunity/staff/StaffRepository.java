package org.hmscommunity.staff;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.hmscommunity.common.IntegrationPendingException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Doktor dizinini Data iş akışının {@code doctors} görünümünden okur (V1 migration'ı).
 *
 * <p>Görünüm yalnızca Hekim rolündeki, {@code doctor_departments} ile etkin bir departmana bağlı ve
 * hesabı devre dışı olmayan personeli içerir; departman ilişkisi yabancı anahtarla korunur (FR-09.1).
 * Veritabanı yapılandırılmamışsa veri uydurulmaz, HTTP 503 döner.
 */
@Repository
public class StaffRepository {

    private static final String SELECT_DOCTORS = """
            SELECT id, display_name, department
            FROM doctors
            """;
    // Departman, soyad ve ad sırası Türkçe harmanlamayla; kimlik, eşitlikte belirlenimci sıra sağlar.
    private static final String ORDER = """
            ORDER BY department COLLATE "tr-TR-x-icu", last_name COLLATE "tr-TR-x-icu",
                     first_name COLLATE "tr-TR-x-icu", id
            """;

    private final JdbcClient jdbcClient;

    public StaffRepository(ObjectProvider<JdbcClient> jdbcClientProvider) {
        this.jdbcClient = jdbcClientProvider.getIfAvailable();
    }

    /**
     * @param department departman adıyla birebir eşleşme; {@code null} ise tüm doktorlar. Eşleşme yoksa boş liste.
     */
    public List<Clinician> findClinicians(String department) {
        if (jdbcClient == null) {
            throw new IntegrationPendingException("doctor directory (#202)");
        }
        if (department != null) {
            return jdbcClient.sql(SELECT_DOCTORS + "WHERE department = ?\n" + ORDER)
                    .param(department)
                    .query((rs, rowNum) -> map(rs))
                    .list();
        }
        return jdbcClient.sql(SELECT_DOCTORS + ORDER)
                .query((rs, rowNum) -> map(rs))
                .list();
    }

    private static Clinician map(ResultSet rs) throws SQLException {
        return new Clinician(rs.getObject("id", UUID.class), rs.getString("display_name"), rs.getString("department"));
    }

    /** OpenAPI {@code Clinician}; {@code displayName}, {@code staff_members.display_name} ile aynıdır. */
    public record Clinician(UUID id, String displayName, String department) {}
}
