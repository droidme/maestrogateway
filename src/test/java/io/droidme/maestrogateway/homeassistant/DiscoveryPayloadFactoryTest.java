package io.droidme.maestrogateway.homeassistant;

import static org.assertj.core.api.Assertions.assertThat;

import io.droidme.maestrogateway.TestProperties;
import io.droidme.maestrogateway.config.MaestroProperties;
import io.droidme.maestrogateway.protocol.InformationRegistry;
import io.droidme.maestrogateway.service.MqttTopics;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DiscoveryPayloadFactoryTest {

    private DiscoveryEntity find(List<DiscoveryEntity> entities, String objectId) {
        return entities.stream()
                .filter(entity -> entity.objectId().equals(objectId))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void createsSensorsWithUnitsAndStateTopics() {
        MaestroProperties properties = TestProperties.defaults();
        DiscoveryPayloadFactory factory = new DiscoveryPayloadFactory(properties, new InformationRegistry(),
                new MqttTopics(properties));

        DiscoveryEntity ambient = find(factory.build(), "ambient_temperature");
        Map<String, Object> payload = ambient.payload();

        assertThat(ambient.component()).isEqualTo("sensor");
        assertThat(payload)
                .containsEntry("name", "Ambient temperature")
                .containsEntry("state_topic", "Maestro/Ambient_Temperature")
                .containsEntry("device_class", "temperature")
                .containsEntry("unit_of_measurement", "°C")
                .containsEntry("unique_id", "mcz_stove_ambient_temperature");
        assertThat(payload).doesNotContainKey("value_template");
    }

    @Test
    void usesAValueTemplateInJsonMode() {
        MaestroProperties properties = TestProperties.bind(Map.of("mqtt.payload-type", "JSON"));
        DiscoveryPayloadFactory factory = new DiscoveryPayloadFactory(properties, new InformationRegistry(),
                new MqttTopics(properties));

        Map<String, Object> payload = find(factory.build(), "ambient_temperature").payload();

        assertThat(payload)
                .containsEntry("state_topic", "Maestro")
                .containsEntry("value_template", "{{ value_json.Ambient_Temperature }}");
    }

    @Test
    void createsSwitchAndNumberControls() {
        MaestroProperties properties = TestProperties.defaults();
        DiscoveryPayloadFactory factory = new DiscoveryPayloadFactory(properties, new InformationRegistry(),
                new MqttTopics(properties));
        List<DiscoveryEntity> entities = factory.build();

        Map<String, Object> power = find(entities, "power").payload();
        assertThat(power)
                .containsEntry("command_topic", "Maestro/Command/Power")
                .containsEntry("payload_on", "1")
                .containsEntry("payload_off", "0")
                .containsEntry("state_topic", "Maestro/Power");

        Map<String, Object> setpoint = find(entities, "temperature_setpoint").payload();
        assertThat(setpoint)
                .containsEntry("command_topic", "Maestro/Command/Temperature_Setpoint")
                .containsEntry("unit_of_measurement", "°C");
        assertThat(((Number) setpoint.get("min")).doubleValue()).isEqualTo(5.0);
        assertThat(((Number) setpoint.get("max")).doubleValue()).isEqualTo(40.0);
        assertThat(((Number) setpoint.get("step")).doubleValue()).isEqualTo(0.5);
    }

    @Test
    void createsButtonsForAlarmResetAndClockSync() {
        MaestroProperties properties = TestProperties.defaults();
        DiscoveryPayloadFactory factory = new DiscoveryPayloadFactory(properties, new InformationRegistry(),
                new MqttTopics(properties));

        assertThat(find(factory.build(), "reset_alarm").payload())
                .containsEntry("command_topic", "Maestro/Command/Reset_Alarm")
                .containsEntry("payload_press", "255");
        assertThat(find(factory.build(), "sync_clock").payload())
                .containsEntry("command_topic", "Maestro/Command/Set_DateTime")
                .containsEntry("payload_press", "NOW");
    }

    @Test
    void doesNotCreateEntitiesForUnknownFields() {
        MaestroProperties properties = TestProperties.defaults();
        DiscoveryPayloadFactory factory = new DiscoveryPayloadFactory(properties, new InformationRegistry(),
                new MqttTopics(properties));

        assertThat(factory.build())
                .noneMatch(entity -> entity.objectId().startsWith("unknown"))
                .noneMatch(entity -> entity.objectId().contains("messagetype"));
    }

    @Test
    void everyEntityHasAUniqueObjectId() {
        MaestroProperties properties = TestProperties.defaults();
        DiscoveryPayloadFactory factory = new DiscoveryPayloadFactory(properties, new InformationRegistry(),
                new MqttTopics(properties));

        assertThat(factory.build())
                .extracting(DiscoveryEntity::objectId)
                .doesNotHaveDuplicates();
    }
}
