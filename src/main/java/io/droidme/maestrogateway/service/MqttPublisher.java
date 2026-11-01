package io.droidme.maestrogateway.service;

import com.google.gson.Gson;
import io.droidme.maestrogateway.config.MaestroProperties;
import io.droidme.maestrogateway.metrics.MaestroMetrics;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.paho.mqtt5.PahoMqtt5Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Publishes state, status and discovery messages to MQTT through Camel's Paho MQTT 5 producer.
 *
 * <p>A single producer endpoint is reused for all topics; the actual topic is passed per message
 * with the {@code CamelPahoMqtt5OverrideTopic} header. This keeps exactly one MQTT publisher
 * connection instead of one per topic.
 */
@Component
public class MqttPublisher {

    private static final Logger log = LoggerFactory.getLogger(MqttPublisher.class);
    private static final String PRODUCER_ENDPOINT = "paho-mqtt5:mcz-publish";
    private static final long FAILURE_LOG_INTERVAL_MS = 60_000;

    private final ObjectProvider<ProducerTemplate> templateProvider;
    private final MaestroProperties properties;
    private final MqttTopics topics;
    private final MaestroMetrics metrics;
    private final Gson gson = new Gson();
    private final String endpointUri;
    private final AtomicLong lastPublishAt = new AtomicLong();
    private final AtomicLong consecutiveFailures = new AtomicLong();
    private final AtomicLong lastFailureLogAt = new AtomicLong();

    public MqttPublisher(ObjectProvider<ProducerTemplate> templateProvider, MaestroProperties properties,
            MqttTopics topics, MaestroMetrics metrics) {
        this.templateProvider = templateProvider;
        this.properties = properties;
        this.topics = topics;
        this.metrics = metrics;
        String clientId = URLEncoder.encode(properties.mqtt().clientId() + "-pub", StandardCharsets.UTF_8);
        this.endpointUri = PRODUCER_ENDPOINT + "?clientId=" + clientId;
    }

    /** Publishes changed fields as one JSON document on the publish prefix. */
    public boolean publishInfo(Map<String, Object> values) {
        if (!properties.mqtt().enabled() || values.isEmpty()) {
            return true;
        }
        return publish(topics.json(), gson.toJson(values), false);
    }

    /** Publishes the {@code state=ON} heartbeat used by the original gateway. */
    public boolean publishState(String state) {
        if (!properties.mqtt().enabled()) {
            return true;
        }
        // The heartbeat keeps its own plain topic; only state and status are JSON.
        return publish(topics.state(), state, false);
    }

    /** Publishes the WebSocket connection status as JSON on the publish prefix. */
    public boolean publishStatus(String status) {
        if (!properties.mqtt().enabled()) {
            return true;
        }
        return publish(topics.json(), gson.toJson(Map.of("Status", status)), false);
    }

    public boolean publishJson(String topic, Object payload, boolean retained) {
        return publish(topic, gson.toJson(payload), retained);
    }

    public boolean publish(String topic, String payload, boolean retained) {
        if (!properties.mqtt().enabled()) {
            return true;
        }
        Map<String, Object> headers = new HashMap<>();
        headers.put(PahoMqtt5Constants.CAMEL_PAHO_OVERRIDE_TOPIC, topic);
        headers.put(PahoMqtt5Constants.CAMEL_PAHO_MSG_QOS, properties.mqtt().qos());
        if (retained) {
            headers.put(PahoMqtt5Constants.CAMEL_PAHO_MSG_RETAINED, true);
        }
        try {
            templateProvider.getObject().sendBodyAndHeaders(endpointUri,
                    payload.getBytes(StandardCharsets.UTF_8), headers);
            lastPublishAt.set(System.currentTimeMillis());
            consecutiveFailures.set(0);
            metrics.publishSuccess();
            return true;
        } catch (Exception e) {
            consecutiveFailures.incrementAndGet();
            metrics.publishFailure();
            logThrottled(topic, e);
            return false;
        }
    }

    private void logThrottled(String topic, Exception failure) {
        long now = System.currentTimeMillis();
        long previous = lastFailureLogAt.get();
        String message = rootMessage(failure);
        if (now - previous > FAILURE_LOG_INTERVAL_MS && lastFailureLogAt.compareAndSet(previous, now)) {
            log.warn("MQTT publish to '{}' failed: {}", topic, message);
        } else {
            log.debug("MQTT publish to '{}' failed: {}", topic, message);
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() != null ? current.getMessage() : current.getClass().getSimpleName();
    }

    /** Epoch millis of the last successful publish, or 0 when nothing was published yet. */
    public long lastPublishAt() {
        return lastPublishAt.get();
    }

    public long consecutiveFailures() {
        return consecutiveFailures.get();
    }
}
