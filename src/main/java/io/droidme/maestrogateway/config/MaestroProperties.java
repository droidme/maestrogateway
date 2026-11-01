package io.droidme.maestrogateway.config;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * All runtime configuration for the gateway.
 *
 * <p>This is an immutable record tree, so constructor binding is used. Every property can be
 * overridden by an environment variable using the standard Spring Boot relaxed binding rules, e.g.
 * {@code maestro.mqtt.broker-url} becomes {@code MAESTRO_MQTT_BROKER_URL}.
 *
 * <p>Defaults live in {@code application.yaml}; a missing section fails fast with a clear message
 * instead of leaving a null group behind.
 *
 * @param mqtt          MQTT broker and topic settings
 * @param stove         stove WebSocket settings
 * @param homeAssistant Home Assistant MQTT discovery settings
 * @param health        actuator health settings
 */
@ConfigurationProperties(prefix = "maestro")
public record MaestroProperties(Mqtt mqtt, Stove stove, HomeAssistant homeAssistant, Health health) {

    public MaestroProperties {
        Objects.requireNonNull(mqtt, "maestro.mqtt must be configured");
        Objects.requireNonNull(stove, "maestro.stove must be configured");
        Objects.requireNonNull(homeAssistant, "maestro.home-assistant must be configured");
        Objects.requireNonNull(health, "maestro.health must be configured");
    }

    /**
     * @param enabled                when disabled, no MQTT consumer is started and all publishes
     *                               become no-ops (used by tests)
     * @param brokerUrl              broker URL, for example {@code tcp://192.168.1.10:1883}
     * @param clientId               base client id; {@code -sub} and {@code -pub} are appended
     * @param username               broker username
     * @param password               broker password
     * @param publishPrefix          prefix for the JSON state topic, without trailing slash
     * @param commandPrefix          JSON command topic, without trailing slash
     * @param qos                    MQTT quality of service used for publishing and subscribing
     * @param startRetryIntervalMs   retry interval while the broker is unreachable at startup
     */
    public record Mqtt(
            boolean enabled,
            String brokerUrl,
            String clientId,
            String username,
            String password,
            String publishPrefix,
            String commandPrefix,
            int qos,
            long startRetryIntervalMs) {
    }

    /**
     * @param autoConnect                connect to the stove once the application is ready
     * @param host                       stove IP address; the official MCZ app expects
     *                                   {@code 192.168.120.1}
     * @param port                       stove WebSocket port
     * @param path                       WebSocket path, normally {@code /}
     * @param connectTimeout             WebSocket connection timeout
     * @param pollIntervalMs             interval between {@code RecuperoInfo} polls
     * @param pingIntervalMs             interval between WebSocket pings
     * @param readTimeoutMs              reconnect when no frame has been received for this long
     * @param reconnectDelayMs           delay between reconnect attempts
     * @param sendLoopTimeoutMs          how long the command sender waits for a command before
     *                                   looping again
     * @param alertAfterFailedReconnects consecutive failed reconnect attempts before a
     *                                   {@code disconnected} status is published
     */
    public record Stove(
            boolean autoConnect,
            String host,
            int port,
            String path,
            Duration connectTimeout,
            long pollIntervalMs,
            long pingIntervalMs,
            long readTimeoutMs,
            long reconnectDelayMs,
            long sendLoopTimeoutMs,
            int alertAfterFailedReconnects) {
    }

    /**
     * @param enabled           publish Home Assistant MQTT discovery entities
     * @param discoveryPrefix   Home Assistant discovery prefix
     * @param deviceId          device identifier used in discovery topics
     * @param deviceName        device display name
     * @param manufacturer      device manufacturer
     * @param model             device model
     * @param initialDelayMs    delay before the first discovery attempt
     * @param retryIntervalMs   retry interval while discovery could not be published
     * @param refreshIntervalMs republish retained discovery documents after this long, so a broker
     *                          restart without retained state does not leave entities missing
     */
    public record HomeAssistant(
            boolean enabled,
            String discoveryPrefix,
            String deviceId,
            String deviceName,
            String manufacturer,
            String model,
            long initialDelayMs,
            long retryIntervalMs,
            long refreshIntervalMs) {
    }

    /**
     * @param failOnStoveDisconnect report {@code DOWN} when the stove WebSocket is not connected;
     *                              defaults to {@code UP} with details
     * @param staleAfter            consider the stove stale when no frame arrived for this long
     */
    public record Health(boolean failOnStoveDisconnect, Duration staleAfter) {
    }
}
