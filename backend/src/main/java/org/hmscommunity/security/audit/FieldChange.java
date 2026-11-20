package org.hmscommunity.security.audit;

/**
 * Güncellemede tek bir alanın önceki ve sonraki değeri.
 * Değerler yalnızca {@link AuditChangeFilter} gizlilik filtresinden geçtikten sonra oluşturulur;
 * kaydedilmesine izin verilmeyen alanlarda değer yerine {@link AuditChangeFilter#REDACTED} bulunur.
 */
public record FieldChange(String field, String before, String after) {
}
