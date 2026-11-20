package org.hmscommunity.common;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Zamana bağlı kuralların (token süresi, denetim kaydı zaman damgası) test edilebilmesi için
 * sistem saati tek bir bean üzerinden enjekte edilir.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
