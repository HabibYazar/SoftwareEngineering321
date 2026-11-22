package org.hmscommunity.departments;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * US-09 (#204): {@code GET /api/v1/departments}. Erişim güvenlik zincirinde
 * {@code DOCTOR_DIRECTORY_VIEW} iznine bağlıdır (US-02).
 */
@RestController
public class DepartmentsController {

    private final DepartmentsService departmentsService;

    public DepartmentsController(DepartmentsService departmentsService) {
        this.departmentsService = departmentsService;
    }

    @GetMapping("/api/v1/departments")
    public List<DepartmentsRepository.Department> listDepartments() {
        return departmentsService.getDepartments();
    }
}
