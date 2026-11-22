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

    public List<Doctor> findDoctors(String department) {
        if (jdbcClient == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Database not configured."
            );
        }
        if (department != null && !department.isBlank()) {
            return jdbcClient.sql("""
                    SELECT id, display_name, department
                    FROM doctors
                    WHERE department = ?
                    ORDER BY display_name ASC
                    """)
                    .param(department)
                    .query((rs, rowNum) -> new Doctor(
                            rs.getObject("id", UUID.class),
                            rs.getString("display_name"),
                            rs.getString("department")
                    ))
                    .list();
        }

        return jdbcClient.sql("""
                SELECT id, display_name, department
                FROM doctors
                ORDER BY display_name ASC
                """)
                .query((rs, rowNum) -> new Doctor(
                        rs.getObject("id", UUID.class),
                        rs.getString("display_name"),
                        rs.getString("department")
                ))
                .list();
    }

    public record Doctor(UUID id, String displayName, String department) {}
}
