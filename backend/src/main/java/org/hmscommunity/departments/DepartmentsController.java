package org.hmscommunity.departments;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;
import java.util.List;
import java.util.stream.IntStream;
@RestController
public class DepartmentsController {

    private final DepartmentsService departmentsService;

    public DepartmentsController(DepartmentsService departmentsService) {
        this.departmentsService = departmentsService;
    }

    @GetMapping("/api/v1/departments")
    public List<DepartmentsRepository.Department> listDepartments() {
        List<DepartmentsRepository.Department> departments = departmentsService.getDepartments();
        return departments;
    }

}
