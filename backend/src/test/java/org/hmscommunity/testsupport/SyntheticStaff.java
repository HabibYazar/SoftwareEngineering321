package org.hmscommunity.testsupport;

import java.util.Locale;
import org.hmscommunity.security.StaffRole;

/**
 * Testlerde kullanılan sentetik personel hesapları.
 * Bu değerler yalnızca test içindir; gerçek bir kişiye veya gerçek bir parolaya karşılık gelmez.
 */
public final class SyntheticStaff {

    /** Yalnızca testlerde kullanılan sentetik parola. */
    public static final String PASSWORD = "synthetic-test-only-Pass1";

    private SyntheticStaff() {
    }

    /** Her rol için deterministik sentetik kullanıcı adı, örn. {@code test.receptionist}. */
    public static String username(StaffRole role) {
        return "test." + role.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
