package org.hmscommunity.testsupport;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/**
 * Depodaki {@code api/openapi.yaml} sözleşmesini okuyarak kod ile sözleşmenin ayrışmasını
 * (ör. enum değerleri) testlerde yakalamaya yarar. Maven testleri {@code backend} dizininden çalıştırır.
 */
public final class OpenApiContract {

    private static final Path CONTRACT = Path.of("..", "api", "openapi.yaml");

    private OpenApiContract() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> schema(String name) {
        Map<String, Object> components = (Map<String, Object>) load().get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
        return (Map<String, Object>) schemas.get(name);
    }

    @SuppressWarnings("unchecked")
    public static List<String> enumOf(String schemaName, String property) {
        Map<String, Object> properties = (Map<String, Object>) schema(schemaName).get("properties");
        Map<String, Object> definition = (Map<String, Object>) properties.get(property);
        return (List<String>) definition.get("enum");
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> operation(String path, String method) {
        Map<String, Object> paths = (Map<String, Object>) load().get("paths");
        Map<String, Object> pathItem = (Map<String, Object>) paths.get(path);
        return pathItem == null ? null : (Map<String, Object>) pathItem.get(method);
    }

    private static Map<String, Object> load() {
        try (InputStream input = Files.newInputStream(CONTRACT)) {
            return new Yaml().load(input);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read OpenAPI contract at " + CONTRACT.toAbsolutePath(), ex);
        }
    }
}
