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

    public String publishPrefix() {
        return stripTrailingSlash(properties.mqtt().publishPrefix());
    }

    public String commandPrefix() {
        return stripTrailingSlash(properties.mqtt().commandPrefix());
    }

    public String info(String key) {
        return join(publishPrefix(), key);
    }

    public String state() {
        return join(publishPrefix(), "state");
    }

    /** Connection status topic ({@code connected}/{@code disconnected}), matching the Python gateway. */
    public String status() {
        return join(publishPrefix(), "Status");
    }

    /** Single JSON payload topic, used when payload type is JSON. */
    public String json() {
        return publishPrefix();
    }

    public String command(String name) {
        return join(commandPrefix(), name);
    }

    public String commandSubscription() {
        String prefix = commandPrefix();
        return prefix.isEmpty() ? "#" : prefix + "/#";
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
