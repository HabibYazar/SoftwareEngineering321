package org.hmscommunity.security;

import jakarta.servlet.DispatcherType;
import org.hmscommunity.security.authentication.AccessTokenService;
import org.hmscommunity.security.authentication.BearerTokenAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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
            .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(problemHandler))
            .addFilterBefore(new BearerTokenAuthenticationFilter(accessTokenService),
                UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(authorize -> authorize
                // Hata yönlendirmesinin (ERROR dispatch) gerçek durum kodunu koruması için izin verilir.
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers("/api/v1/health").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").authenticated()
                // Açıkça tanımlanmamış her yol reddedilir (varsayılan olarak kapalı).
                .anyRequest().denyAll())
            .build();
    }

    /** Personel parola özetleri BCrypt ile doğrulanır (#114 veri modeliyle uzlaşılan biçim). */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
