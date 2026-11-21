package org.hmscommunity.testsupport;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.hmscommunity.security.AuthenticatedStaff;
import org.hmscommunity.security.StaffRole;
import org.hmscommunity.security.authentication.LoginFailureReason;
import org.hmscommunity.security.authentication.StaffAccount;
import org.hmscommunity.security.authentication.StaffSession;
import org.hmscommunity.security.authentication.StaffSessionStore;
import org.hmscommunity.security.authorization.Permission;

/**
 * Veritabanısız testlerde {@code auth_*} SQL fonksiyonlarının oturum davranışını taklit eden test dublörü.
 *
 * <p>İzinler, V1/V3 migration'larının kurduğu başlangıç matrisinin aynısıdır (Administrator her izne,
 * Receptionist hasta oluşturma/görüntüleme/güncellemeye sahiptir; diğer rollerin izni yoktur). Gerçek
 * veritabanının aynı sonuçları verdiği PostgreSQL entegrasyon testlerinde ayrıca doğrulanır. Denetim
 * kaydı taklit edilmez; giriş/çıkış kayıtları yalnızca gerçek veritabanı testlerinde doğrulanır.
 */
public class InMemoryStaffSessionStore implements StaffSessionStore {

    /** V1/V3 migration'larındaki başlangıç rol–izin matrisi. */
    public static final Map<StaffRole, Set<Permission>> INITIAL_DATABASE_MATRIX = Map.of(
        StaffRole.ADMINISTRATOR, EnumSet.allOf(Permission.class),
        StaffRole.RECEPTIONIST,
        EnumSet.of(Permission.PATIENT_CREATE, Permission.PATIENT_VIEW, Permission.PATIENT_UPDATE));

    /** {@code auth_login_failure} çağrısı: eşleşen hesap ve neden. */
    public record FailedLogin(StaffAccount matchedAccount, LoginFailureReason reason) {
    }

    private record StoredSession(UUID sessionId, StaffAccount account, Instant expiresAt) {
    }

    private final Clock clock;
    private final Map<String, StoredSession> sessionsByTokenHash = new ConcurrentHashMap<>();
    private final Set<UUID> deactivatedAccounts = ConcurrentHashMap.newKeySet();
    private final List<FailedLogin> failedLogins = new CopyOnWriteArrayList<>();
    private volatile RuntimeException failure;

    public InMemoryStaffSessionStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Optional<UUID> open(StaffAccount account, byte[] tokenHash, Instant expiresAt) {
        failIfRequested();
        if (deactivatedAccounts.contains(account.id())) {
            return Optional.empty();
        }
        UUID sessionId = UUID.randomUUID();
        sessionsByTokenHash.put(HexFormat.of().formatHex(tokenHash), new StoredSession(sessionId, account, expiresAt));
        return Optional.of(sessionId);
    }

    @Override
    public void recordFailedLogin(StaffAccount matchedAccount, LoginFailureReason reason) {
        failIfRequested();
        failedLogins.add(new FailedLogin(matchedAccount, reason));
    }

    @Override
    public Optional<StaffSession> findActive(byte[] tokenHash) {
        StoredSession stored = sessionsByTokenHash.get(HexFormat.of().formatHex(tokenHash));
        if (stored == null || !clock.instant().isBefore(stored.expiresAt())
                || deactivatedAccounts.contains(stored.account().id())) {
            return Optional.empty();
        }
        StaffAccount account = stored.account();
        return Optional.of(new StaffSession(
            new AuthenticatedStaff(account.id(), account.username(), account.role(), stored.sessionId()),
            stored.expiresAt(),
            INITIAL_DATABASE_MATRIX.getOrDefault(account.role(), Set.of())));
    }

    @Override
    public boolean close(UUID sessionId) {
        return sessionsByTokenHash.values().removeIf(session -> session.sessionId().equals(sessionId));
    }

    /** Hesabı devre dışı bırakır; veritabanında olduğu gibi açık oturumları da sonlanır. */
    public void deactivate(UUID accountId) {
        deactivatedAccounts.add(accountId);
        sessionsByTokenHash.values().removeIf(session -> session.account().id().equals(accountId));
    }

    public int activeSessionCount() {
        return sessionsByTokenHash.size();
    }

    public List<FailedLogin> failedLogins() {
        return List.copyOf(failedLogins);
    }

    /** Depo arızasını taklit eder; {@code null} ile normale döner. */
    public void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    public void reset() {
        sessionsByTokenHash.clear();
        deactivatedAccounts.clear();
        failedLogins.clear();
        failure = null;
    }

    private void failIfRequested() {
        if (failure != null) {
            throw failure;
        }
    }
}
