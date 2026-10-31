package io.droidme.maestrogateway.metrics;

import io.droidme.maestrogateway.service.CommandQueue;
import io.droidme.maestrogateway.service.MaestroWebSocketClient;
import io.droidme.maestrogateway.service.MqttPublisher;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers gauges for connection state, queue size and message ages. */
@Configuration
public class MetricsConfig {

    @Bean
    Gauge stoveConnectedGauge(MeterRegistry registry, MaestroWebSocketClient client) {
        return Gauge.builder("maestro.stove.connected", client, value -> value.isConnected() ? 1 : 0)
                .description("1 when the stove WebSocket is connected")
                .register(registry);
    }

    @Bean
    Gauge commandQueueGauge(MeterRegistry registry, CommandQueue queue) {
        return Gauge.builder("maestro.command.queue.size", queue, CommandQueue::size)
                .description("Commands waiting to be written to the stove")
                .register(registry);
    }

    @Bean
    Gauge lastFrameAgeGauge(MeterRegistry registry, MaestroWebSocketClient client) {
        return Gauge.builder("maestro.stove.last_frame.age.seconds", client, value -> {
                    long last = value.lastFrameAt();
                    return last == 0 ? -1 : (System.currentTimeMillis() - last) / 1000.0;
                })
                .description("Seconds since the last frame received from the stove (-1 when never)")
                .register(registry);
    }

    @Bean
    Gauge lastPublishAgeGauge(MeterRegistry registry, MqttPublisher publisher) {
        return Gauge.builder("maestro.mqtt.last_publish.age.seconds", publisher, value -> {
                    long last = value.lastPublishAt();
                    return last == 0 ? -1 : (System.currentTimeMillis() - last) / 1000.0;
                })
                .description("Seconds since the last successful MQTT publish (-1 when never)")
                .register(registry);
    }
}
