package org.hmscommunity.staff;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Repository
public class StaffRepository {

    private final JdbcClient jdbcClient;

    public StaffRepository(ObjectProvider<JdbcClient> jdbcClientProvider) {
        this.jdbcClient = jdbcClientProvider.getIfAvailable();
    }

    public List<Clinician> findClinicians(String department) {
        if (jdbcClient == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Database not configured."
            );
        }
        if (department != null && !department.isBlank()) {
            return jdbcClient.sql("""
                    SELECT id, display_name, department
                    FROM clinicians
                    WHERE department = ?
                    ORDER BY row_num ASC
                    """)
                    .param(department)
                    .query((rs, rowNum) -> new Clinician(
                            rs.getObject("id", UUID.class),
                            rs.getString("display_name"),
                            rs.getString("department")
                    ))
                    .list();
        }

        return jdbcClient.sql("""
                SELECT id, display_name, department
                FROM clinicians
                ORDER BY row_num ASC
                """)
                .query((rs, rowNum) -> new Clinician(
                        rs.getObject("id", UUID.class),
                        rs.getString("display_name"),
                        rs.getString("department")
                ))
                .list();
    }

    public record Clinician(UUID id, String displayName, String department) {}
}
