package io.droidme.maestrogateway;

import io.droidme.maestrogateway.config.MaestroProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

/**
 * Builds {@link MaestroProperties} instances for tests.
 *
 * <p>Properties are immutable records that are normally bound by Spring Boot, so tests cannot create
 * or mutate them directly. This helper binds the real {@code application.yaml} (so the tests also
 * catch missing defaults) and applies optional overrides on top, using the same relaxed binding rules
 * as production.
 */
public final class TestProperties {

    private static final MaestroProperties DEFAULTS = bind(Map.of());

    private TestProperties() {
    }

    /** Properties bound from {@code application.yaml} without overrides. */
    public static MaestroProperties defaults() {
        return DEFAULTS;
    }

    /**
     * Properties bound from {@code application.yaml} plus the given overrides.
     *
     * @param overrides property names relative to the {@code maestro} prefix, for example
     *                  {@code mqtt.payload-type}; a name already starting with {@code maestro.} is
     *                  used as-is
     */
    public static MaestroProperties bind(Map<String, Object> overrides) {
        try {
            MockEnvironment environment = new MockEnvironment();
            List<PropertySource<?>> yamlSources = new YamlPropertySourceLoader()
                    .load("application", new ClassPathResource("application.yaml"));
            yamlSources.forEach(source -> environment.getPropertySources().addLast(source));

            Map<String, Object> prefixed = new LinkedHashMap<>();
            overrides.forEach((key, value) -> prefixed.put(prefixedKey(key), value));
            if (!prefixed.isEmpty()) {
                environment.getPropertySources()
                        .addFirst(new MapPropertySource("testOverrides", prefixed));
            }

            Binder binder = new Binder(
                    ConfigurationPropertySources.get(environment),
                    new PropertySourcesPlaceholdersResolver(environment),
                    ApplicationConversionService.getSharedInstance());
            return binder.bind("maestro", Bindable.of(MaestroProperties.class))
                    .orElseThrow(() -> new IllegalStateException("Could not bind maestro properties"));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read application.yaml", e);
        }
    }

    private static String prefixedKey(String key) {
        return key.startsWith("maestro.") ? key : "maestro." + key;
    }
}
