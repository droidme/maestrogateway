package io.droidme.maestrogateway.service;

import io.droidme.maestrogateway.config.MaestroProperties;
import org.springframework.stereotype.Component;

/** Builds every MQTT topic from the configured prefixes. */
@Component
public class MqttTopics {

    private final MaestroProperties properties;

    public MqttTopics(MaestroProperties properties) {
        this.properties = properties;
    }

    /** Single JSON state/status topic, e.g. {@code Maestro}. */
    public String json() {
        return stripTrailingSlash(properties.mqtt().publishPrefix());
    }

    /** Dedicated heartbeat topic, e.g. {@code Maestro/state}. */
    public String state() {
        return join(json(), "state");
    }

    /** JSON command topic, e.g. {@code Maestro/Command}. */
    public String commandTopic() {
        return stripTrailingSlash(properties.mqtt().commandPrefix());
    }

    private static String join(String prefix, String suffix) {
        return prefix.isEmpty() ? suffix : prefix + "/" + suffix;
    }

    private static String stripTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("/+$", "");
    }
}
