package io.droidme.maestrogateway.service;

import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * Publishes {@code Maestro/Status} transitions.
 *
 * <p>Publishes {@code connected} on every successful connect and {@code disconnected} only after the
 * configured number of failed reconnects, so a short network hiccup does not produce noise.
 */
@Component
public class MqttStatusPublisher implements ConnectionListener {

    private final MqttPublisher publisher;
    private final AtomicReference<String> lastStatus = new AtomicReference<>();

    public MqttStatusPublisher(MqttPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void onConnected() {
        lastStatus.set("connected");
        publisher.publishStatus("connected");
    }

    @Override
    public void onDisconnected(boolean alert) {
        if (alert && !"disconnected".equals(lastStatus.getAndSet("disconnected"))) {
            publisher.publishStatus("disconnected");
        }
    }
}
