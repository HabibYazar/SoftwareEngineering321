package org.hmscommunity.departments;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Repository
public class DepartmentsRepository {

    private final JdbcClient jdbcClient;

    public DepartmentsRepository(ObjectProvider<JdbcClient> jdbcClientProvider) {
        this.jdbcClient = jdbcClientProvider.getIfAvailable();
    }

    public List<Department> findDepartments() {
        if (jdbcClient == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Database not configured."
            );
        }
        return jdbcClient.sql("""
                SELECT id, name
                FROM departments
                ORDER BY name ASC
                """)
                .query((rs, rowNum) -> new Department(
                        rs.getObject("id", UUID.class),
                        rs.getString("name")
                ))
                .list();
    }

    public record Department(UUID id, String department) {}
}