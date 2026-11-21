package org.hmscommunity.departments;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class DepartmentsRepository {

    private final JdbcClient jdbcClient;

    public DepartmentsRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<Department> findDepartments() {
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