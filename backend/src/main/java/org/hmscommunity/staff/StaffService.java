package org.hmscommunity.staff;

import java.util.List;
import org.hmscommunity.common.web.FieldViolation;
import org.hmscommunity.common.web.RequestValidationException;
import org.springframework.stereotype.Service;

/** US-09 (#204): departmana göre doktor listeleme. */
@Service
public class StaffService {

    /** OpenAPI {@code listClinicians.department} için üst sınır. */
    static final int MAX_DEPARTMENT_LENGTH = 100;

    private final StaffRepository staffRepository;

    public StaffService(StaffRepository staffRepository) {
        this.staffRepository = staffRepository;
    }

    /**
     * Boş veya yalnızca boşluk içeren filtre "tüm doktorlar" anlamına gelir. Bilinmeyen ya da doktoru
     * olmayan bir departman hata değil, boş listedir (FR-09.3).
     */
    public List<StaffRepository.Clinician> getClinicians(String department) {
        String filter = department == null || department.isBlank() ? null : department.strip();
        if (filter != null && filter.length() > MAX_DEPARTMENT_LENGTH) {
            throw new RequestValidationException(List.of(new FieldViolation("department", "Size",
                "size must be between 1 and " + MAX_DEPARTMENT_LENGTH)));
        }
        return staffRepository.findClinicians(filter);
    }
}
