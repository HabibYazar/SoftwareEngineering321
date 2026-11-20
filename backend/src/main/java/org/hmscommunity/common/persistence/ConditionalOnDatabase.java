package org.hmscommunity.common.persistence;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * Bean'i yalnızca bir veritabanı adresi ({@code spring.datasource.url}, ortamda {@code HMS_DB_URL})
 * yapılandırıldığında etkinleştirir.
 *
 * <p>Koşul ortam değişkenine bakar, bean varlığına bakmaz; bu nedenle bileşen taramasıyla bulunan
 * sınıflarda da güvenilir biçimde çalışır. Adres yoksa PostgreSQL adaptörleri hiç oluşturulmaz ve
 * {@code @Fallback} işaretli fail-closed uygulamalar devrede kalır.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(ConditionalOnDatabase.DatabaseConfiguredCondition.class)
public @interface ConditionalOnDatabase {

    /** {@code spring.datasource.url} boş değilse eşleşir. */
    class DatabaseConfiguredCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return StringUtils.hasText(context.getEnvironment().getProperty("spring.datasource.url"));
        }
    }
}
