package io.droidme.maestrogateway.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.droidme.maestrogateway.TestProperties;
import io.droidme.maestrogateway.config.MaestroProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MqttTopicsTest {

    @Test
    void buildsTopicsFromTheConfiguredPrefixes() {
        MqttTopics topics = new MqttTopics(TestProperties.defaults());

        assertThat(topics.json()).isEqualTo("Maestro");
        assertThat(topics.state()).isEqualTo("Maestro/state");
        assertThat(topics.commandTopic()).isEqualTo("Maestro/Command");
    }

    @Test
    void toleratesTrailingSlashesInTheConfiguration() {
        MaestroProperties properties = TestProperties.bind(Map.of(
                "mqtt.publish-prefix", "Home/Stove/",
                "mqtt.command-prefix", "Home/Stove/Command/"));
        MqttTopics topics = new MqttTopics(properties);

        assertThat(topics.json()).isEqualTo("Home/Stove");
        assertThat(topics.state()).isEqualTo("Home/Stove/state");
        assertThat(topics.commandTopic()).isEqualTo("Home/Stove/Command");
    }
}
