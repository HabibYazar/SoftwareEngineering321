package org.hmscommunity.staff;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class StaffController {

    private final StaffService staffService;

    public StaffController(StaffService staffService) {
        this.staffService = staffService;
    }

    @GetMapping("/api/v1/staff")
    public List<StaffRepository.Doctor> listDoctors(@RequestParam(required = false) String department) {
        return staffService.getDoctors(department);
    }
}
