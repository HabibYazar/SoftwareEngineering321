package org.hmscommunity.patients.api;

import java.net.URI;
import org.hmscommunity.patients.application.PatientService;
import org.hmscommunity.patients.domain.Patient;
import org.hmscommunity.security.AuthenticatedStaff;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Hasta uç noktaları. Rol kontrolü güvenlik filtre zincirinde (US-02) yapılır;
 * buraya yalnızca yetkili ve kimliği doğrulanmış istekler ulaşır.
 */
@RestController
@RequestMapping("/api/v1/patients")
public class PatientController {

    private final PatientService patientService;

    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    /** US-06 (#179): hasta kaydı — 201, çakışan ulusal kimlik numarasında 409. */
    @PostMapping
    public ResponseEntity<Patient> register(@RequestBody PatientRequest request,
            @AuthenticationPrincipal AuthenticatedStaff actor) {
        Patient patient = patientService.register(request.normalizedNationalId(), request.toDetails(), actor);
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
            .path("/{id}").buildAndExpand(patient.id()).toUri();
        return ResponseEntity.created(location).body(patient);
    }
}
