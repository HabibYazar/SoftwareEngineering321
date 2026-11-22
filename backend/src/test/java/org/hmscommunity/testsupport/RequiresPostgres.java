package org.hmscommunity.testsupport;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Testi yalnızca tek kullanımlık bir PostgreSQL sunucusu yapılandırılmışsa çalıştırır
 * (bkz. {@link PostgresTestDatabase}). Aksi halde JUnit testi "skipped" olarak raporlar; atlanan bir
 * test veritabanı entegrasyonunun doğrulandığı anlamına gelmez.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("postgres")
@EnabledIfEnvironmentVariable(named = PostgresTestDatabase.URL_VARIABLE, matches = ".+",
    disabledReason = "HMS_TEST_DB_URL is not set; real PostgreSQL integration tests were skipped")
public @interface RequiresPostgres {
}
