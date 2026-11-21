package org.hmscommunity.common.persistence;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * PostgreSQL bağlantı havuzu. Spring Boot'un {@code DataSourceAutoConfiguration}'ı adres olmadan uygulamayı
 * başlatmadığı için devre dışıdır; aynı {@code spring.datasource.*} ayarlarıyla veri kaynağı burada kurulur.
 *
 * <p>Veri kaynağı tanımlandığında Spring Boot şunları kendiliğinden ekler: Flyway migration'ları,
 * {@code JdbcClient} ve {@code @Transactional} için işlem yöneticisi. Adres yoksa bunların hiçbiri oluşmaz.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnDatabase
@EnableConfigurationProperties(DataSourceProperties.class)
public class DatabaseConfiguration {

    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    HikariDataSource dataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }
}
