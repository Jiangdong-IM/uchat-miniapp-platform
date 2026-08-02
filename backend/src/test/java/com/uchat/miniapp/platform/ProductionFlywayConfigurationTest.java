package com.uchat.miniapp.platform;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionFlywayConfigurationTest {
    private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

    @Test
    void productionBaselinesExistingSchemaAtZeroSoV1StillRuns() throws IOException {
        List<PropertySource<?>> production = loader.load(
                "production", new ClassPathResource("application-production.yml"));

        assertThat(value(production, "spring.flyway.baseline-on-migrate")).isEqualTo(true);
        assertThat(String.valueOf(value(production, "spring.flyway.baseline-version"))).isEqualTo("0");
    }

    @Test
    void localDoesNotEnableFlywayBaseline() throws IOException {
        List<PropertySource<?>> local = loader.load(
                "local", new ClassPathResource("application-local.yml"));

        assertThat(value(local, "spring.flyway.baseline-on-migrate")).isNull();
        assertThat(value(local, "spring.flyway.baseline-version")).isNull();
    }

    private static Object value(List<PropertySource<?>> sources, String key) {
        return sources.stream().map(source -> source.getProperty(key))
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }
}
