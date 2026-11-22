package org.hmscommunity.departments;

import java.util.List;
import org.hmscommunity.departments.DepartmentsRepository.Department;
import org.springframework.stereotype.Service;

/** US-09: doktor dizinindeki departman seçeneklerini sağlar. */
@Service
public class DepartmentsService {

    private final DepartmentsRepository departmentsRepository;

    public DepartmentsService(DepartmentsRepository departmentsRepository) {
        this.departmentsRepository = departmentsRepository;
    }

    public List<Department> getDepartments() {
        return departmentsRepository.findDepartments();
    }
}
