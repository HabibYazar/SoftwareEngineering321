package org.hmscommunity.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
public class HttpSecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Scaffold is read-only. Future endpoints MUST implement authentication,
        // authorization and CSRF/session decisions before adding any business routes.
        return http
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/api/v1/health").permitAll()
                .anyRequest().denyAll())
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())
            .build();
    }
}
