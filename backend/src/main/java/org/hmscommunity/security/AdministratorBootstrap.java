package org.hmscommunity.security;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.hmscommunity.common.persistence.ConditionalOnDatabase;
import org.hmscommunity.common.persistence.PostgresErrors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * İlk yönetici hesabını, Data iş akışının belgelediği dağıtım adımıyla oluşturur:
 * {@code SELECT security_bootstrap_admin(...)} ({@code docs/database/README.md} §1).
 *
 * <p>Güvenlik kuralları:
 * <ul>
 *   <li>Yalnızca {@code HMS_BOOTSTRAP_ADMIN_USERNAME} ve {@code HMS_BOOTSTRAP_ADMIN_PASSWORD} birlikte
 *       verildiğinde çalışır; kaynak kodda veya depoda hiçbir kullanıcı adı ya da parola yoktur.</li>
 *   <li>Tekrarlanabilir ve yıkıcı değildir: sistemde bir yönetici varsa hiçbir şey yapmaz, mevcut hesapları
 *       değiştirmez. Fonksiyonun kendi kilidi eşzamanlı iki başlatmada ikinci yöneticiyi de engeller.</li>
 *   <li>Parola yalnızca BCrypt özeti olarak veritabanına gider ve hiçbir log satırına yazılmaz.</li>
 *   <li>Bir hata uygulamanın açılmasını engellemez; yönetici oluşmaz ve neden (değer içermeden) loglanır.</li>
 * </ul>
 * Fonksiyon PUBLIC'ten geri alınmıştır; en az yetkili {@code hms_app} kullanıcısıyla çalışan bir kurulumda bu
 * adım yetki hatası verir ve migration kullanıcısıyla elle çalıştırılmalıdır.
 */
@Component
@ConditionalOnDatabase
class AdministratorBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdministratorBootstrap.class);
    /** Veritabanının kullanıcı adı kuralı ({@code field_rules}, V1). */
    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9._-]{3,50}$");
    static final int MIN_PASSWORD_LENGTH = 12;
    /** BCrypt yalnızca ilk 72 baytı kullanır; daha uzun parolalar reddedilir. */
    static final int MAX_PASSWORD_BYTES = 72;

    private final JdbcClient jdbc;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;
    private final String firstName;
    private final String lastName;

    AdministratorBootstrap(JdbcClient jdbc, PasswordEncoder passwordEncoder,
            @Value("${hms.bootstrap.admin.username:}") String username,
            @Value("${hms.bootstrap.admin.password:}") String password,
            @Value("${hms.bootstrap.admin.first-name:Sistem}") String firstName,
            @Value("${hms.bootstrap.admin.last-name:Yöneticisi}") String lastName) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.username = username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
        this.password = password == null ? "" : password;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        bootstrap();
    }

    /** Sonucu döndürür; testlerde ve loglarda hangi yolun izlendiği görülebilsin diye ayrı tutulur. */
    Outcome bootstrap() {
        if (username.isEmpty() && password.isEmpty()) {
            return Outcome.NOT_CONFIGURED;
        }
        if (username.isEmpty() || password.isEmpty()) {
            log.warn("Administrator bootstrap skipped: HMS_BOOTSTRAP_ADMIN_USERNAME and "
                + "HMS_BOOTSTRAP_ADMIN_PASSWORD must be set together.");
            return Outcome.REJECTED;
        }
        if (!USERNAME.matcher(username).matches()) {
            log.error("Administrator bootstrap skipped: the username must match {}.", USERNAME.pattern());
            return Outcome.REJECTED;
        }
        int passwordBytes = password.getBytes(StandardCharsets.UTF_8).length;
        if (password.length() < MIN_PASSWORD_LENGTH || passwordBytes > MAX_PASSWORD_BYTES) {
            log.error("Administrator bootstrap skipped: the password must be at least {} characters and at most {} bytes.",
                MIN_PASSWORD_LENGTH, MAX_PASSWORD_BYTES);
            return Outcome.REJECTED;
        }
        if (administratorExists()) {
            log.info("Administrator bootstrap skipped: an administrator already exists.");
            return Outcome.ALREADY_PRESENT;
        }
        try {
            UUID accountId = jdbc.sql("SELECT security_bootstrap_admin(?, ?, ?, ?)")
                .params(firstName, lastName, username, passwordEncoder.encode(password))
                .query(UUID.class)
                .single();
            log.warn("First administrator account created (username={}, account={}). "
                + "Remove HMS_BOOTSTRAP_ADMIN_PASSWORD from the environment.", username, accountId);
            return Outcome.CREATED;
        } catch (DataAccessException failure) {
            String state = PostgresErrors.serverError(failure).map(message -> message.getSQLState()).orElse("unknown");
            if ("HS008".equals(state)) {
                log.info("Administrator bootstrap skipped: an administrator was created concurrently.");
                return Outcome.ALREADY_PRESENT;
            }
            log.error("Administrator bootstrap failed with SQLSTATE {}. If the runtime user lacks EXECUTE on "
                + "security_bootstrap_admin, run the deployment step with the migration user.", state);
            return Outcome.FAILED;
        }
    }

    private boolean administratorExists() {
        return jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM staff_members s JOIN roles r ON r.id = s.role_id
                               WHERE r.code = 'Administrator')
                """)
            .query(Boolean.class)
            .single();
    }

    enum Outcome { NOT_CONFIGURED, REJECTED, ALREADY_PRESENT, CREATED, FAILED }
}
