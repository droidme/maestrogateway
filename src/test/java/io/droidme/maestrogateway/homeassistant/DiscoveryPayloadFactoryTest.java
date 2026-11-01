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

    private List<DiscoveryEntity> entities() {
        MaestroProperties properties = TestProperties.defaults();
        return new DiscoveryPayloadFactory(properties, new InformationRegistry(),
                new MqttTopics(properties)).build();
    }

    @Test
    void createsSensorsWithUnitsAndJsonStateTopics() {
        DiscoveryEntity ambient = find(entities(), "ambient_temperature");
        Map<String, Object> payload = ambient.payload();

        assertThat(ambient.component()).isEqualTo("sensor");
        assertThat(payload)
                .containsEntry("name", "Ambient temperature")
                .containsEntry("state_topic", "Maestro")
                .containsEntry("value_template", "{{ value_json.Ambient_Temperature }}")
                .containsEntry("device_class", "temperature")
                .containsEntry("unit_of_measurement", "°C")
                .containsEntry("unique_id", "mcz_stove_ambient_temperature");
    }

    @Test
    void createsSwitchAndNumberControls() {
        List<DiscoveryEntity> entities = entities();

        Map<String, Object> power = find(entities, "power").payload();
        assertThat(power)
                .containsEntry("command_topic", "Maestro/Command")
                .containsEntry("payload_on", "{\"Command\":\"Power\",\"Value\":\"1\"}")
                .containsEntry("payload_off", "{\"Command\":\"Power\",\"Value\":\"0\"}")
                .containsEntry("state_topic", "Maestro")
                .containsEntry("value_template", "{{ value_json.Power }}");

        Map<String, Object> setpoint = find(entities, "temperature_setpoint").payload();
        assertThat(setpoint)
                .containsEntry("command_topic", "Maestro/Command")
                .containsEntry("command_template",
                        "{\"Command\":\"Temperature_Setpoint\",\"Value\":\"{{ value }}\"}")
                .containsEntry("unit_of_measurement", "°C");
        assertThat(((Number) setpoint.get("min")).doubleValue()).isEqualTo(5.0);
        assertThat(((Number) setpoint.get("max")).doubleValue()).isEqualTo(40.0);
        assertThat(((Number) setpoint.get("step")).doubleValue()).isEqualTo(0.5);
    }

    @Test
    void createsButtonsForAlarmResetAndClockSync() {
        List<DiscoveryEntity> entities = entities();

        assertThat(find(entities, "reset_alarm").payload())
                .containsEntry("command_topic", "Maestro/Command")
                .containsEntry("payload_press", "{\"Command\":\"Reset_Alarm\",\"Value\":\"255\"}");
        assertThat(find(entities, "sync_clock").payload())
                .containsEntry("command_topic", "Maestro/Command")
                .containsEntry("payload_press", "{\"Command\":\"Set_DateTime\",\"Value\":\"NOW\"}");
    }

    @Test
    void doesNotCreateEntitiesForUnknownFields() {
        assertThat(entities())
                .noneMatch(entity -> entity.objectId().startsWith("unknown"))
                .noneMatch(entity -> entity.objectId().contains("messagetype"));
    }

    @Test
    void everyEntityHasAUniqueObjectId() {
        assertThat(entities())
                .extracting(DiscoveryEntity::objectId)
                .doesNotHaveDuplicates();
    }
}
