package org.hmscommunity.departments;

import java.util.List;
import java.util.UUID;
import org.hmscommunity.common.IntegrationPendingException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Departmanları PostgreSQL'deki {@code departments} tablosundan okur (V1 migration'ı).
 * Yalnızca etkin departmanlar listelenir; bu, {@code doctors} görünümünün kuralıyla aynıdır.
 * Veritabanı yapılandırılmamışsa veri uydurulmaz, HTTP 503 döner.
 */
@Repository
public class DepartmentsRepository {

    private final JdbcClient jdbcClient;

    public DepartmentsRepository(ObjectProvider<JdbcClient> jdbcClientProvider) {
        this.jdbcClient = jdbcClientProvider.getIfAvailable();
    }

    public List<Department> findDepartments() {
        if (jdbcClient == null) {
            throw new IntegrationPendingException("doctor directory (#202)");
        }
        // Sıralama, sunucunun varsayılan yerel ayarından bağımsız olarak Türkçe harmanlamayla yapılır.
        return jdbcClient.sql("""
                SELECT id, name
                FROM departments
                WHERE is_active
                ORDER BY name COLLATE "tr-TR-x-icu", id
                """)
                .query((rs, rowNum) -> new Department(rs.getObject("id", UUID.class), rs.getString("name")))
                .list();
    }

    /** OpenAPI {@code Department}: {@code id} ve {@code name}. */
    public record Department(UUID id, String name) {}
}
