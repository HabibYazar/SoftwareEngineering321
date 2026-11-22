package org.hmscommunity.security;

import jakarta.servlet.DispatcherType;
import java.util.Map;
import org.hmscommunity.security.authentication.AccessTokenService;
import org.hmscommunity.security.authentication.BearerTokenAuthenticationFilter;
import org.hmscommunity.security.authorization.Permission;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class HttpSecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AccessTokenService accessTokenService,
            SecurityProblemHandler problemHandler) throws Exception {
        return http
            // Kimlik doğrulama yalnızca Authorization başlığındaki bearer token ile yapılır; çerez tabanlı
            // oturum olmadığından CSRF saldırı yüzeyi yoktur ve sunucu HTTP oturumu oluşturmaz.
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            // Spring'in varsayılan /logout yönlendirmesi kapatılır; çıkış POST /api/v1/auth/logout ile yapılır.
            .logout(AbstractHttpConfigurer::disable)
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(problemHandler)
                .accessDeniedHandler(problemHandler))
            .addFilterBefore(new BearerTokenAuthenticationFilter(accessTokenService),
                UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(authorize -> authorize
                // Hata yönlendirmesinin (ERROR dispatch) gerçek durum kodunu koruması için izin verilir.
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers("/api/v1/health").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").authenticated()
                // US-02: izinler her istekte oturumla birlikte veritabanından okunur (bkz. Permission).
                .requestMatchers(HttpMethod.POST, "/api/v1/patients").hasAuthority(Permission.PATIENT_CREATE.authority())
                .requestMatchers(HttpMethod.GET, "/api/v1/patients").hasAuthority(Permission.PATIENT_VIEW.authority())
                .requestMatchers(HttpMethod.GET, "/api/v1/patients/*").hasAuthority(Permission.PATIENT_VIEW.authority())
                .requestMatchers(HttpMethod.PUT, "/api/v1/patients/*").hasAuthority(Permission.PATIENT_UPDATE.authority())
                // US-09: doktor dizini ve departman listesi anonim değildir (SRS FR-09.10).
                .requestMatchers(HttpMethod.GET, "/api/v1/staff", "/api/v1/departments")
                    .hasAuthority(Permission.DOCTOR_DIRECTORY_VIEW.authority())
                // Açıkça tanımlanmamış her yol reddedilir (varsayılan olarak kapalı):
                // anonim istek 401, kimliği doğrulanmış istek 403 alır.
                .anyRequest().denyAll())
            .build();
    }

    /**
     * Personel parola özetleri BCrypt ile doğrulanır. Veritabanı özetleri ön ekli ({@code {bcrypt}$2a$...},
     * README'deki ilk yönetici örneği) veya ön eksiz ({@code $2a$...}) saklayabildiği için ikisi de kabul edilir.
     * Şemanın izin verdiği Argon2 özetleri backend'de desteklenmez; bu hesaplar giriş yapamaz (fail-closed).
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
        DelegatingPasswordEncoder encoder = new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", bcrypt));
        encoder.setDefaultPasswordEncoderForMatches(bcrypt);
        return encoder;
    }
}
