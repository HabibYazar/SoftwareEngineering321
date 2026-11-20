package org.hmscommunity.security.authentication;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * {@code Authorization: Bearer <token>} başlığını okuyup geçerli oturumu güvenlik bağlamına yerleştirir.
 *
 * <p>Geçersiz, süresi dolmuş veya iptal edilmiş tokenlar sessizce yok sayılır; istek anonim kalır ve
 * korumalı uç noktalarda giriş noktası (entry point) HTTP 401 döndürür. Token değeri asla loglanmaz.
 * Bu filtre bilinçli olarak bir Spring bean'i değildir; aksi halde servlet kapsayıcısına ikinci kez kaydedilirdi.
 */
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AccessTokenService accessTokenService;
    private final SecurityContextHolderStrategy contextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();

    public BearerTokenAuthenticationFilter(AccessTokenService accessTokenService) {
        this.accessTokenService = accessTokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            String rawToken = header.substring(BEARER_PREFIX.length()).trim();
            accessTokenService.resolve(rawToken).ifPresent(session -> {
                SecurityContext context = contextHolderStrategy.createEmptyContext();
                context.setAuthentication(new StaffAuthenticationToken(session));
                contextHolderStrategy.setContext(context);
            });
        }
        chain.doFilter(request, response);
    }
}
