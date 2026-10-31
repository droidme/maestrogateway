package io.droidme.maestrogateway.health;

import io.droidme.maestrogateway.config.MaestroProperties;
import io.droidme.maestrogateway.service.CommandQueue;
import io.droidme.maestrogateway.service.MaestroWebSocketClient;
import io.droidme.maestrogateway.service.MqttPublisher;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Actuator health indicator for the stove link and MQTT publishing.
 *
 * <p>By default a disconnected stove is still reported as {@code UP}, with details, so a container
 * or supervisor does not restart the gateway when the stove is simply switched off. Set
 * {@code maestro.health.fail-on-stove-disconnect=true} for strict behaviour.
 */
@Component("maestro")
public class MaestroHealthIndicator implements HealthIndicator {

    private final MaestroWebSocketClient webSocketClient;
    private final MqttPublisher publisher;
    private final CommandQueue commandQueue;
    private final MaestroProperties properties;

    public MaestroHealthIndicator(MaestroWebSocketClient webSocketClient, MqttPublisher publisher,
            CommandQueue commandQueue, MaestroProperties properties) {
        this.webSocketClient = webSocketClient;
        this.publisher = publisher;
        this.commandQueue = commandQueue;
        this.properties = properties;
    }

    @Override
    public Health health() {
        long now = System.currentTimeMillis();
        boolean connected = webSocketClient.isConnected();
        Long frameAge = webSocketClient.lastFrameAt() == 0
                ? null
                : (now - webSocketClient.lastFrameAt()) / 1000;
        Long publishAge = publisher.lastPublishAt() == 0
                ? null
                : (now - publisher.lastPublishAt()) / 1000;
        boolean stale = connected && frameAge != null
                && frameAge > properties.health().staleAfter().toSeconds();

        Map<String, Object> stove = new LinkedHashMap<>();
        stove.put("connected", connected);
        stove.put("failedReconnects", webSocketClient.failedReconnects());
        stove.put("lastFrameAgeSeconds", frameAge);
        stove.put("stale", stale);

        Map<String, Object> mqtt = new LinkedHashMap<>();
        mqtt.put("broker", properties.mqtt().brokerUrl());
        mqtt.put("lastPublishAgeSeconds", publishAge);
        mqtt.put("consecutiveFailures", publisher.consecutiveFailures());

        Health.Builder builder = Health.up()
                .withDetail("stove", stove)
                .withDetail("mqtt", mqtt)
                .withDetail("commandQueueSize", commandQueue.size());

        if (properties.health().failOnStoveDisconnect() && (!connected || stale)) {
            return builder.down().build();
        }
        return builder.build();
    }
}
