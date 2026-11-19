package org.hmscommunity.staff;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StaffService {

    private final StaffRepository staffRepository;

    public StaffService(StaffRepository staffRepository) {
        this.staffRepository = staffRepository;
    }

    public List<StaffRepository.Clinician> getClinicians(String department) {
        return staffRepository.findClinicians(department);
    }
}
