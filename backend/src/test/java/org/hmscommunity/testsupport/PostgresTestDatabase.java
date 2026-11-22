package org.hmscommunity.testsupport;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import org.flywaydb.core.Flyway;
import org.hmscommunity.security.StaffRole;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Gerçek PostgreSQL entegrasyon testleri için tek kullanımlık veritabanı.
 *
 * <p>Etkinleştirmek için ortam değişkenleri tek kullanımlık bir PostgreSQL sunucusunu göstermelidir:
 * {@code HMS_TEST_DB_URL} (ör. {@code jdbc:postgresql://localhost:55432/postgres}), {@code HMS_TEST_DB_USER}
 * ve {@code HMS_TEST_DB_PASSWORD} (veritabanı ve rol oluşturabilen yönetici). Tanımlı değilse PostgreSQL
 * testleri ATLANIR (başarılı sayılmaz). Paylaşılan veya üretim sunucusu KULLANILMAMALIDIR.
 *
 * <p>Her test çalıştırması (JVM) için:
 * <ol>
 *   <li>Adı zaman damgalı yeni bir veritabanı oluşturulur; mevcut veritabanlarına dokunulmaz, hiçbir şey silinmez.</li>
 *   <li>Data iş akışının V1..V3 migration'ları Flyway ile yönetici olarak uygulanır (olay tetikleyicileri dahil).</li>
 *   <li>Uygulama, README §1'deki gibi yalnızca {@code hms_app} rolünün üyesi olan ayrı bir giriş kullanıcısıyla
 *       bağlanır; böylece veritabanının yetki katmanı testlerde de uygulanır.</li>
 *   <li>İlk yönetici belgelenen dağıtım adımıyla ({@code security_bootstrap_admin}), diğer altı rolün hesapları
 *       bu yöneticinin aktör bağlamında, veritabanının izin kontrollerinden geçerek oluşturulur. Hepsi sentetiktir.</li>
 * </ol>
 */
public final class PostgresTestDatabase {

    public static final String URL_VARIABLE = "HMS_TEST_DB_URL";
    /** Devre dışı bırakılmış sentetik hesap (Receptionist rolünde). */
    public static final String DEACTIVATED_USERNAME = "test.deactivated";

    private static volatile PostgresTestDatabase instance;

    private final String jdbcUrl;
    private final String adminUser;
    private final String adminPassword;
    private final String appUser = "hms_it_app";
    private final String appPassword;
    private final JdbcClient admin;
    private final Map<StaffRole, UUID> accountIds = new EnumMap<>(StaffRole.class);
    private final UUID deactivatedAccountId;

    private PostgresTestDatabase() {
        String serverUrl = requiredVariable(URL_VARIABLE);
        adminUser = requiredVariable("HMS_TEST_DB_USER");
        adminPassword = requiredVariable("HMS_TEST_DB_PASSWORD");
        String databaseName = "hms_it_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
            + "_" + randomHex(3);
        JdbcClient server = JdbcClient.create(new DriverManagerDataSource(serverUrl, adminUser, adminPassword));
        server.sql("CREATE DATABASE " + databaseName + " ENCODING 'UTF8' TEMPLATE template0").update();
        jdbcUrl = serverUrl.substring(0, serverUrl.lastIndexOf('/') + 1) + databaseName;

        Flyway.configure().dataSource(jdbcUrl, adminUser, adminPassword).load().migrate();

        appPassword = randomHex(24);
        admin = JdbcClient.create(new DriverManagerDataSource(jdbcUrl, adminUser, adminPassword));
        admin.sql("""
            DO $$ BEGIN
                IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'hms_it_app') THEN
                    CREATE ROLE hms_it_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE;
                END IF;
            END $$""").update();
        admin.sql("ALTER ROLE hms_it_app WITH LOGIN PASSWORD '" + appPassword + "'").update();
        admin.sql("GRANT hms_app TO hms_it_app").update();

        String passwordHash = new BCryptPasswordEncoder(4).encode(SyntheticStaff.PASSWORD);
        accountIds.put(StaffRole.ADMINISTRATOR, admin.sql("SELECT security_bootstrap_admin('Sentetik', 'Yonetici', ?, ?)")
            .params(SyntheticStaff.username(StaffRole.ADMINISTRATOR), passwordHash).query(UUID.class).single());
        for (StaffRole role : StaffRole.values()) {
            if (role != StaffRole.ADMINISTRATOR) {
                accountIds.put(role, createAccount(role, SyntheticStaff.username(role), passwordHash));
            }
        }
        deactivatedAccountId = createAccount(StaffRole.RECEPTIONIST, DEACTIVATED_USERNAME, passwordHash);
        asAdministrator("UPDATE user_accounts SET status = 'DEACTIVATED' WHERE id = ?", deactivatedAccountId);
    }

    public static PostgresTestDatabase get() {
        if (instance == null) {
            synchronized (PostgresTestDatabase.class) {
                if (instance == null) {
                    instance = new PostgresTestDatabase();
                }
            }
        }
        return instance;
    }

    public String jdbcUrl() {
        return jdbcUrl;
    }

    public String adminUser() {
        return adminUser;
    }

    public String adminPassword() {
        return adminPassword;
    }

    public String appUser() {
        return appUser;
    }

    public String appPassword() {
        return appPassword;
    }

    /** Doğrulama sorguları için yönetici bağlantısı (uygulama bu bağlantıyı kullanmaz). */
    public JdbcClient admin() {
        return admin;
    }

    public UUID accountId(StaffRole role) {
        return accountIds.get(role);
    }

    public UUID deactivatedAccountId() {
        return deactivatedAccountId;
    }

    /** Tek bir ifadeyi ilk yöneticinin aktör bağlamında, kendi işleminde çalıştırır. */
    public void asAdministrator(String sql, Object... parameters) {
        admin.sql("""
            DO $$ BEGIN
                PERFORM hms_set_context('%s'::uuid, NULL);
                %s;
            END $$""".formatted(accountIds.get(StaffRole.ADMINISTRATOR), bind(sql, parameters))).update();
    }

    private UUID createAccount(StaffRole role, String username, String passwordHash) {
        UUID staffId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        asAdministrator("INSERT INTO staff_members (id, role_id, first_name, last_name) "
            + "SELECT ?, id, 'Sentetik', ? FROM roles WHERE code = ?", staffId, role.apiName(), role.apiName());
        asAdministrator("INSERT INTO user_accounts (id, staff_member_id, username, password_hash) VALUES (?, ?, ?, ?)",
            accountId, staffId, username, passwordHash);
        return accountId;
    }

    /** DO bloğu parametre almadığı için yalnızca test sabitleri ve UUID'ler güvenli biçimde yerleştirilir. */
    private static String bind(String sql, Object... parameters) {
        String bound = sql;
        for (Object parameter : parameters) {
            String literal = "'" + parameter.toString().replace("'", "''") + "'";
            bound = bound.replaceFirst("\\?", Matcher.quoteReplacement(literal));
        }
        return bound;
    }

    private static String requiredVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must point to a disposable PostgreSQL server");
        }
        return value;
    }

    private static String randomHex(int bytes) {
        byte[] random = new byte[bytes];
        new SecureRandom().nextBytes(random);
        return HexFormat.of().formatHex(random);
    }
}
