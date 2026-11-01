package io.droidme.maestrogateway.homeassistant;

import io.droidme.maestrogateway.config.MaestroProperties;
import io.droidme.maestrogateway.protocol.InfoField;
import io.droidme.maestrogateway.protocol.InformationRegistry;
import io.droidme.maestrogateway.protocol.ValueType;
import io.droidme.maestrogateway.service.MqttTopics;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Builds Home Assistant MQTT discovery documents for the stove.
 *
 * <p>Every information field becomes a sensor (or binary sensor for on/off fields). A curated set
 * of controls becomes switches, numbers and buttons. State is read from the single JSON topic via
 * {@code value_template}; controls publish JSON commands to the configured command topic.
 */
@Component
public class DiscoveryPayloadFactory {

    private static final String SUPPORT_URL = "https://github.com/Chibald/maestrogateway";
    private static final Set<String> DIAGNOSTIC_FIELDS = Set.of(
            "FirmwareVersion", "DatabaseID", "Modbus_Address", "SetHealth",
            "WifiSondeTemperature1", "WifiSondeTemperature2", "WifiSondeTemperature3");

    /** Fields that are exposed as a control entity; no duplicate read-only sensor is created. */
    private static final Set<String> CONTROL_FIELDS = Set.of(
            "Eco_Mode", "Silent_Mode", "Active_Mode", "Chronostat", "Sleep", "Sound_Effects",
            "Temperature_Setpoint", "Boiler_Setpoint", "Power_Level", "Fan_State");

    private final MaestroProperties properties;
    private final InformationRegistry information;
    private final MqttTopics topics;

    public DiscoveryPayloadFactory(MaestroProperties properties, InformationRegistry information, MqttTopics topics) {
        this.properties = properties;
        this.information = information;
        this.topics = topics;
    }

    public List<DiscoveryEntity> build() {
        List<DiscoveryEntity> entities = new ArrayList<>();
        for (InfoField field : information.all()) {
            if (field.index() == 0 || field.name().startsWith("Unknown")) {
                continue;
            }
            if (CONTROL_FIELDS.contains(field.name())) {
                continue;
            }
            entities.add(sensor(field));
        }
        entities.add(switchEntity("power", "Power", "Power", "Power", "mdi:power"));
        entities.add(switchEntity("eco_mode", "Eco mode", "Eco_Mode", "Eco_Mode", "mdi:leaf"));
        entities.add(switchEntity("silent_mode", "Silent mode", "Silent_Mode", "Silent_Mode", "mdi:volume-off"));
        entities.add(switchEntity("active_mode", "Active mode", "Active_Mode", "Active_Mode", "mdi:auto-mode"));
        entities.add(switchEntity("chronostat", "Chronostat", "Chronostat", "Chronostat", "mdi:calendar-clock"));
        entities.add(switchEntity("sleep", "Sleep", "Sleep", "Sleep", "mdi:sleep"));
        entities.add(switchEntity("sound_effects", "Sound effects", "Sound_Effects", "Sound_Effects", "mdi:volume-high"));
        entities.add(switchEntity("diagnostics", "Diagnostics", "Diagnostics", "Diagnostics", "mdi:wrench"));

        entities.add(numberEntity("temperature_setpoint", "Target temperature", "Temperature_Setpoint",
                "Temperature_Setpoint", 5, 40, 0.5, "°C", "temperature", "mdi:thermometer"));
        entities.add(numberEntity("boiler_setpoint", "Boiler setpoint", "Boiler_Setpoint", "Boiler_Setpoint",
                30, 80, 0.5, "°C", "temperature", "mdi:water-boiler"));
        entities.add(numberEntity("power_level", "Power level", "Power_Level", "Power_Level",
                1, 5, 1, null, null, "mdi:fire"));
        entities.add(numberEntity("fan_state", "Fan speed", "Fan_State", "Fan_State",
                0, 6, 1, null, null, "mdi:fan"));

        entities.add(button("reset_alarm", "Reset alarm", "Reset_Alarm", "255", "mdi:bell-off"));
        entities.add(button("sync_clock", "Sync clock", "Set_DateTime", "NOW", "mdi:clock-check"));
        return entities;
    }

    private DiscoveryEntity sensor(InfoField field) {
        boolean binary = field.type() == ValueType.ON_OFF;
        String component = binary ? "binary_sensor" : "sensor";
        String objectId = slug(field.name());
        Map<String, Object> payload = base(humanize(field.name()), objectId);
        applyState(payload, field.name());
        switch (field.type()) {
            case TEMPERATURE -> {
                payload.put("device_class", "temperature");
                payload.put("unit_of_measurement", "°C");
                payload.put("state_class", "measurement");
                payload.put("suggested_display_precision", 1);
            }
            case TIMESPAN -> payload.put("icon", "mdi:timer-outline");
            case THREE_WAY_VALVE -> payload.put("icon", "mdi:valve");
            case BRAZIER -> payload.put("icon", "mdi:fire");
            case ON_OFF -> {
                payload.put("payload_on", "1");
                payload.put("payload_off", "0");
            }
            case INT -> {
                if ("Power_Level".equals(field.name())) {
                    payload.put("icon", "mdi:fire");
                }
            }
        }
        if (DIAGNOSTIC_FIELDS.contains(field.name())) {
            payload.put("entity_category", "diagnostic");
        }
        return new DiscoveryEntity(component, objectId, payload);
    }

    private DiscoveryEntity switchEntity(String objectId, String name, String command, String stateKey, String icon) {
        Map<String, Object> payload = base(name, objectId);
        payload.put("command_topic", topics.commandTopic());
        payload.put("payload_on", jsonCommand(command, "1"));
        payload.put("payload_off", jsonCommand(command, "0"));
        payload.put("state_on", "1");
        payload.put("state_off", "0");
        payload.put("icon", icon);
        applyState(payload, stateKey);
        return new DiscoveryEntity("switch", objectId, payload);
    }

    private DiscoveryEntity numberEntity(String objectId, String name, String command, String stateKey,
            double min, double max, double step, String unit, String deviceClass, String icon) {
        Map<String, Object> payload = base(name, objectId);
        payload.put("command_topic", topics.commandTopic());
        payload.put("command_template", jsonCommandTemplate(command));
        payload.put("min", min);
        payload.put("max", max);
        payload.put("step", step);
        payload.put("mode", "box");
        payload.put("icon", icon);
        if (unit != null) {
            payload.put("unit_of_measurement", unit);
        }
        if (deviceClass != null) {
            payload.put("device_class", deviceClass);
        }
        applyState(payload, stateKey);
        return new DiscoveryEntity("number", objectId, payload);
    }

    private DiscoveryEntity button(String objectId, String name, String command, String payloadPress, String icon) {
        Map<String, Object> payload = base(name, objectId);
        payload.put("command_topic", topics.commandTopic());
        payload.put("payload_press", jsonCommand(command, payloadPress));
        payload.put("icon", icon);
        return new DiscoveryEntity("button", objectId, payload);
    }

    private Map<String, Object> base(String name, String objectId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", name);
        payload.put("unique_id", properties.homeAssistant().deviceId() + "_" + objectId);
        payload.put("object_id", objectId);
        payload.put("device", device());
        payload.put("origin", Map.of(
                "name", "maestrogateway",
                "support_url", SUPPORT_URL));
        return payload;
    }

    private Map<String, Object> device() {
        MaestroProperties.HomeAssistant ha = properties.homeAssistant();
        Map<String, Object> device = new LinkedHashMap<>();
        device.put("identifiers", List.of(ha.deviceId()));
        device.put("name", ha.deviceName());
        device.put("manufacturer", ha.manufacturer());
        device.put("model", ha.model());
        return device;
    }

    private void applyState(Map<String, Object> payload, String key) {
        payload.put("state_topic", topics.json());
        payload.put("value_template", "{{ value_json." + key + " }}");
    }

    private static String jsonCommand(String command, String value) {
        return "{\"Command\":\"" + command + "\",\"Value\":\"" + value + "\"}";
    }

    private static String jsonCommandTemplate(String command) {
        return "{\"Command\":\"" + command + "\",\"Value\":\"{{ value }}\"}";
    }

    private static String humanize(String name) {
        String text = name.replace('_', ' ')
                .replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                .replaceAll("\\s+", " ")
                .trim();
        return text.charAt(0) + text.substring(1).toLowerCase(Locale.ROOT);
    }

    static String slug(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replaceAll("[^A-Za-z0-9]+", "_")
                .toLowerCase(Locale.ROOT)
                .replaceAll("^_+|_+$", "");
    }
}
