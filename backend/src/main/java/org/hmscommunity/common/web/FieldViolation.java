package org.hmscommunity.common.web;

/**
 * Alan düzeyindeki tek bir doğrulama hatası.
 * Gizlilik gereği reddedilen değer istemciye asla geri gönderilmez;
 * yalnızca alan yolu, makine tarafından okunabilir kod ve açıklama döner.
 */
public record FieldViolation(String field, String code, String message) {
}
