package org.hmscommunity.staff;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class StaffRepository {

    private final JdbcClient jdbcClient;

    public StaffRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<Clinician> findClinicians(String department) {
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
