package io.droidme.maestrogateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.droidme.maestrogateway.TestProperties;
import io.droidme.maestrogateway.metrics.MaestroMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.paho.mqtt5.PahoMqtt5Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class MqttPublisherTest {

    private final ProducerTemplate template = mock(ProducerTemplate.class);
    private MqttPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = publisher(provider(), Map.of());
    }

    private MqttPublisher publisher(ObjectProvider<ProducerTemplate> provider, Map<String, Object> overrides) {
        var properties = TestProperties.bind(overrides);
        return new MqttPublisher(provider, properties, new MqttTopics(properties),
                new MaestroMetrics(new SimpleMeterRegistry()));
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Map<String, Object>> headers() {
        return ArgumentCaptor.forClass(Map.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishesASingleJsonDocument() {
        assertThat(publisher.publishInfo(Map.of("Ambient_Temperature", 23.5))).isTrue();

        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Map<String, Object>> headers = headers();
        verify(template).sendBodyAndHeaders(anyString(), body.capture(), headers.capture());
        assertThat(headers.getValue()).containsEntry(PahoMqtt5Constants.CAMEL_PAHO_OVERRIDE_TOPIC, "Maestro");
        assertThat(new String((byte[]) body.getValue(), StandardCharsets.UTF_8))
                .isEqualTo("{\"Ambient_Temperature\":23.5}");
    }

    @Test
    @SuppressWarnings("unchecked")
    void keepsTheHeartbeatOnItsOwnTopic() {
        assertThat(publisher.publishState("ON")).isTrue();

        ArgumentCaptor<Map<String, Object>> headers = headers();
        verify(template).sendBodyAndHeaders(anyString(), any(), headers.capture());
        assertThat(headers.getValue()).containsEntry(PahoMqtt5Constants.CAMEL_PAHO_OVERRIDE_TOPIC, "Maestro/state");
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishesTheStatusAsJson() {
        assertThat(publisher.publishStatus("connected")).isTrue();

        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Map<String, Object>> headers = headers();
        verify(template).sendBodyAndHeaders(anyString(), body.capture(), headers.capture());
        assertThat(headers.getValue()).containsEntry(PahoMqtt5Constants.CAMEL_PAHO_OVERRIDE_TOPIC, "Maestro");
        assertThat(new String((byte[]) body.getValue(), StandardCharsets.UTF_8)).isEqualTo("{\"Status\":\"connected\"}");
    }

    @Test
    @SuppressWarnings("unchecked")
    void marksDiscoveryMessagesAsRetained() {
        assertThat(publisher.publishJson("homeassistant/sensor/x/config", Map.of("name", "x"), true)).isTrue();

        ArgumentCaptor<Map<String, Object>> headers = headers();
        verify(template).sendBodyAndHeaders(anyString(), any(), headers.capture());
        assertThat(headers.getValue()).containsEntry(PahoMqtt5Constants.CAMEL_PAHO_MSG_RETAINED, true);
    }

    @Test
    void doesNothingWhenMqttIsDisabled() {
        publisher = publisher(provider(), Map.of("mqtt.enabled", false));

        assertThat(publisher.publishInfo(Map.of("Ambient_Temperature", 23.5))).isTrue();
        assertThat(publisher.publishState("ON")).isTrue();
        assertThat(publisher.publishStatus("connected")).isTrue();

        verify(template, never()).sendBodyAndHeaders(anyString(), any(), anyMap());
        assertThat(publisher.consecutiveFailures()).isZero();
    }

    @Test
    void countsAndSwallowsPublishFailures() {
        org.mockito.Mockito.doThrow(new IllegalStateException("broker down"))
                .when(template).sendBodyAndHeaders(anyString(), any(), anyMap());

        assertThat(publisher.publish("Maestro/state", "ON", false)).isFalse();
        assertThat(publisher.publish("Maestro/state", "ON", false)).isFalse();

        assertThat(publisher.consecutiveFailures()).isEqualTo(2);
        verify(template, times(2)).sendBodyAndHeaders(anyString(), eq("ON".getBytes(StandardCharsets.UTF_8)), anyMap());
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<ProducerTemplate> provider() {
        ObjectProvider<ProducerTemplate> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(template);
        return provider;
    }
}
