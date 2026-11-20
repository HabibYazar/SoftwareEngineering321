package org.hmscommunity.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Henüz sağlanmamış kalıcılık entegrasyonlarının yerine geçen test dublörleri.
 * Üretimdeki geçici uygulamalar {@code @Fallback} olduğundan buradaki bean'ler otomatik olarak tercih edilir;
 * bu, veri sahibinin gerçek uygulamayı eklediğinde de aynı mekanizmanın çalışacağını gösterir.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestDoublesConfiguration {

    @Bean
    InMemoryStaffAccountDirectory inMemoryStaffAccountDirectory() {
        return new InMemoryStaffAccountDirectory();
    }
}
