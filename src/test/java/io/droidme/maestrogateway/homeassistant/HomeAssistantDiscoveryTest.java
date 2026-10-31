package io.droidme.maestrogateway.homeassistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.droidme.maestrogateway.TestProperties;
import io.droidme.maestrogateway.config.MaestroProperties;
import io.droidme.maestrogateway.protocol.InformationRegistry;
import io.droidme.maestrogateway.service.MqttPublisher;
import io.droidme.maestrogateway.service.MqttTopics;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HomeAssistantDiscoveryTest {

    private final MqttPublisher publisher = mock(MqttPublisher.class);
    private HomeAssistantDiscovery discovery;
    private int entityCount;

    @BeforeEach
    void setUp() {
        use(TestProperties.defaults());
    }

    private void use(MaestroProperties properties) {
        DiscoveryPayloadFactory factory = new DiscoveryPayloadFactory(properties, new InformationRegistry(),
                new MqttTopics(properties));
        discovery = new HomeAssistantDiscovery(properties, factory, publisher);
        entityCount = factory.build().size();
    }

    @Test
    void publishesRetainedConfigTopicsOnce() {
        when(publisher.publishJson(anyString(), any(), eq(true))).thenReturn(true);

        discovery.publishAll();
        discovery.publishAll();

        assertThat(discovery.isPublished()).isTrue();
        verify(publisher, times(entityCount)).publishJson(anyString(), any(), eq(true));
    }

    @Test
    void refreshesAfterTheConfiguredInterval() {
        use(TestProperties.bind(Map.of("home-assistant.refresh-interval-ms", 0)));
        when(publisher.publishJson(anyString(), any(), eq(true))).thenReturn(true);

        discovery.publishAll();
        discovery.publishAll();

        verify(publisher, times(entityCount * 2)).publishJson(anyString(), any(), eq(true));
    }

    @Test
    void retriesWhenPublishingFails() {
        when(publisher.publishJson(anyString(), any(), eq(true))).thenReturn(false);

        discovery.publishAll();

        assertThat(discovery.isPublished()).isFalse();
        verify(publisher, times(entityCount)).publishJson(anyString(), any(), eq(true));
    }

    @Test
    void doesNothingWhenDisabled() {
        use(TestProperties.bind(Map.of("home-assistant.enabled", false)));

        discovery.publishAll();

        verify(publisher, never()).publishJson(anyString(), any(), eq(true));
    }
}
