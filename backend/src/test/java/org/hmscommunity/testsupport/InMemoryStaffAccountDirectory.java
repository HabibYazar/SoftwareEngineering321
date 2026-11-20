package org.hmscommunity.testsupport;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.hmscommunity.security.StaffRole;
import org.hmscommunity.security.authentication.StaffAccount;
import org.hmscommunity.security.authentication.StaffAccountDirectory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * #114 veri modeli hazır olana kadar backend davranışını doğrulamak için kullanılan test dublörü.
 * Parolalar üretim ile aynı biçimde BCrypt özeti olarak saklanır (testi hızlandırmak için düşük maliyetle).
 */
public class InMemoryStaffAccountDirectory implements StaffAccountDirectory {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(4);
    private static final Map<StaffRole, String> SEEDED_HASHES = new ConcurrentHashMap<>();

    private final Map<String, StaffAccount> accounts = new ConcurrentHashMap<>();

    public InMemoryStaffAccountDirectory() {
        resetToSyntheticAccounts();
    }

    /** Her rol için bir sentetik hesap oluşturur; testler arasında durumu sıfırlamak için kullanılır. */
    public final void resetToSyntheticAccounts() {
        accounts.clear();
        for (StaffRole role : StaffRole.values()) {
            String hash = SEEDED_HASHES.computeIfAbsent(role, r -> ENCODER.encode(SyntheticStaff.PASSWORD));
            String username = SyntheticStaff.username(role);
            accounts.put(username, new StaffAccount(stableId(username), username, hash, role));
        }
    }

    public StaffAccount add(String username, String rawPassword, StaffRole role) {
        StaffAccount account = new StaffAccount(stableId(username), username, ENCODER.encode(rawPassword), role);
        accounts.put(username, account);
        return account;
    }

    public StaffAccount get(StaffRole role) {
        return accounts.get(SyntheticStaff.username(role));
    }

    @Override
    public Optional<StaffAccount> findByUsername(String username) {
        return Optional.ofNullable(accounts.get(username));
    }

    private static UUID stableId(String username) {
        return UUID.nameUUIDFromBytes(("staff:" + username).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
