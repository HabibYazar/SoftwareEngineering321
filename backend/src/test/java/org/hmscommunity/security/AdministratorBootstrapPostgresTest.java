package org.hmscommunity.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.hmscommunity.security.AdministratorBootstrap.Outcome;
import org.hmscommunity.testsupport.PostgresTestDatabase;
import org.hmscommunity.testsupport.RequiresPostgres;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * İlk yönetici oluşturma adımının gerçek PostgreSQL üzerinde güvenli ve tekrarlanabilir olduğunu doğrular.
 * Her test, yalnızca şema migration'ları uygulanmış ayrı bir veritabanında çalışır.
 */
@RequiresPostgres
@ExtendWith(OutputCaptureExtension.class)
class AdministratorBootstrapPostgresTest {

    /** Yalnızca bu test çalışmasında üretilen sentetik parola; hiçbir ortamda kullanılmaz. */
    private static final String PASSWORD = "synthetic-Bootstrap-Pass-7";

    private final PostgresTestDatabase database = PostgresTestDatabase.get();

    @Test
    void createsTheFirstAdministratorOnceAndThenSkips(CapturedOutput output) {
        JdbcClient owner = ownerOf(database.createSchemaOnlyDatabase());

        assertThat(bootstrap(owner, "First.Admin", PASSWORD).bootstrap()).isEqualTo(Outcome.CREATED);
        assertThat(bootstrap(owner, "second.admin", PASSWORD).bootstrap()).isEqualTo(Outcome.ALREADY_PRESENT);

        Map<String, Object> account = owner.sql("""
                SELECT a.username, a.password_hash, a.must_change_password, r.code AS role
                FROM user_accounts a JOIN staff_members s ON s.id = a.staff_member_id JOIN roles r ON r.id = s.role_id
                """).query().singleRow();
        assertThat(account).containsEntry("username", "first.admin").containsEntry("role", "Administrator")
            .containsEntry("must_change_password", true);
        assertThat(new BCryptPasswordEncoder().matches(PASSWORD, account.get("password_hash").toString()
            .replace("{bcrypt}", ""))).isTrue();
        assertThat(output.getAll()).doesNotContain(PASSWORD);
    }

    @Test
    void doesNothingWithoutConfigurationAndRejectsWeakInput() {
        JdbcClient owner = ownerOf(database.createSchemaOnlyDatabase());

        assertThat(bootstrap(owner, "", "").bootstrap()).isEqualTo(Outcome.NOT_CONFIGURED);
        assertThat(bootstrap(owner, "admin", "").bootstrap()).isEqualTo(Outcome.REJECTED);
        assertThat(bootstrap(owner, "admin", "short").bootstrap()).isEqualTo(Outcome.REJECTED);
        assertThat(bootstrap(owner, "admin", "x".repeat(73)).bootstrap()).isEqualTo(Outcome.REJECTED);
        assertThat(bootstrap(owner, "bad user!", PASSWORD).bootstrap()).isEqualTo(Outcome.REJECTED);
        assertThat(owner.sql("SELECT count(*) FROM user_accounts").query(Long.class).single()).isZero();
    }

    /** En az yetkili çalışma zamanı kullanıcısı fonksiyonu çalıştıramaz; uygulama yine de açılır. */
    @Test
    void leastPrivilegedRuntimeUserCannotBootstrap(CapturedOutput output) {
        String url = database.createSchemaOnlyDatabase();
        JdbcClient application = database.connect(url, database.appUser(), database.appPassword());

        assertThat(bootstrap(application, "first.admin", PASSWORD).bootstrap()).isEqualTo(Outcome.FAILED);
        assertThat(ownerOf(url).sql("SELECT count(*) FROM user_accounts").query(Long.class).single()).isZero();
        assertThat(output.getAll()).contains("SQLSTATE 42501").doesNotContain(PASSWORD);
    }

    private JdbcClient ownerOf(String url) {
        return database.connect(url, database.adminUser(), database.adminPassword());
    }

    private static AdministratorBootstrap bootstrap(JdbcClient jdbc, String username, String password) {
        return new AdministratorBootstrap(jdbc, new HttpSecurityConfiguration().passwordEncoder(), username, password,
            "Sentetik", "Yonetici");
    }
}
