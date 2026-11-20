package org.hmscommunity.security.authorization;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.hmscommunity.security.StaffRole;

/**
 * Rol–izin matrisinin backend'deki tek doğruluk kaynağı (US-02, #132).
 *
 * <p>ÖNEMLİ: Product Owner (@huseyin-tutak) tarafından onaylanmış ayrıntılı rol–izin matrisi henüz
 * mevcut değildir. Bu nedenle burada yalnızca mevcut kabul kriterlerinin açıkça belirttiği yetkiler
 * tanımlanmıştır; kalan tüm kombinasyonlar varsayılan olarak reddedilir (HTTP 403):
 * <ul>
 *   <li>US-06 "As a receptionist, I want to register a new patient" → Receptionist</li>
 *   <li>US-07 "As a receptionist, I want to search patients" → Receptionist</li>
 *   <li>US-08 "As a receptionist, I want to update a patient's information" → Receptionist
 *       (güncelleme için kaydın görüntülenmesi de gerekir)</li>
 * </ul>
 * Administrator, Physician, Nurse vb. rollerin hasta verilerine erişimi onaylı matrise kadar
 * bilinçli olarak AÇILMAMIŞTIR. Matris onaylandığında yalnızca bu sınıf güncellenmelidir.
 */
public final class RolePermissions {

    private static final Map<Permission, Set<StaffRole>> GRANTS;

    static {
        EnumMap<Permission, Set<StaffRole>> grants = new EnumMap<>(Permission.class);
        grants.put(Permission.PATIENT_REGISTER, EnumSet.of(StaffRole.RECEPTIONIST));
        grants.put(Permission.PATIENT_SEARCH, EnumSet.of(StaffRole.RECEPTIONIST));
        grants.put(Permission.PATIENT_VIEW, EnumSet.of(StaffRole.RECEPTIONIST));
        grants.put(Permission.PATIENT_UPDATE, EnumSet.of(StaffRole.RECEPTIONIST));
        for (Permission permission : Permission.values()) {
            // Tanımsız bir izin sessizce "herkese açık" ya da "kimseye kapalı" kalmasın diye başlangıçta hata verilir.
            if (!grants.containsKey(permission)) {
                throw new IllegalStateException("No role grant defined for permission " + permission);
            }
        }
        grants.replaceAll((permission, roles) -> Collections.unmodifiableSet(roles));
        GRANTS = Collections.unmodifiableMap(grants);
    }

    private RolePermissions() {
    }

    public static Set<StaffRole> rolesFor(Permission permission) {
        return GRANTS.get(permission);
    }

    public static boolean isGranted(StaffRole role, Permission permission) {
        return GRANTS.get(permission).contains(role);
    }

    /** Spring Security kuralı için yetki adları, örn. {@code ROLE_RECEPTIONIST}. */
    public static String[] authoritiesFor(Permission permission) {
        return GRANTS.get(permission).stream().map(StaffRole::authority).toArray(String[]::new);
    }
}
