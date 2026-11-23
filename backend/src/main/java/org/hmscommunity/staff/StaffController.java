package org.hmscommunity.staff;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * US-09 (#204): {@code GET /api/v1/staff?department=...}. Erişim güvenlik zincirinde
 * {@code DOCTOR_DIRECTORY_VIEW} iznine bağlıdır (US-02).
 */
@RestController
public class StaffController {

    private final StaffService staffService;

    public StaffController(StaffService staffService) {
        this.staffService = staffService;
    }

    @GetMapping("/api/v1/staff")
    public List<StaffRepository.Clinician> listClinicians(@RequestParam(required = false) String department) {
        return staffService.getClinicians(department);
    }
}
