package io.droidme.maestrogateway.route;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.droidme.maestrogateway.config.MaestroProperties;
import io.droidme.maestrogateway.metrics.MaestroMetrics;
import io.droidme.maestrogateway.protocol.CommandDefinition;
import io.droidme.maestrogateway.protocol.CommandRegistry;
import io.droidme.maestrogateway.protocol.PayloadType;
import io.droidme.maestrogateway.service.ChangeDetector;
import io.droidme.maestrogateway.service.CommandQueue;
import io.droidme.maestrogateway.service.PendingCommand;
import java.util.Optional;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.paho.mqtt5.PahoMqtt5Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Turns an MQTT command message into a {@link PendingCommand}.
 *
 * <p>In {@code TOPIC} mode the command name is the last topic segment
 * ({@code Maestro/Command/Power}) and the payload is the value. In {@code JSON} mode the payload is
 * {@code {"Command":"Power","Value":1}}. A JSON payload on a topic message is also accepted.
 */
@Component
public class CommandIngestProcessor implements Processor {

    private static final Logger log = LoggerFactory.getLogger(CommandIngestProcessor.class);

    private final MaestroProperties properties;
    private final CommandRegistry registry;
    private final CommandQueue queue;
    private final ChangeDetector changeDetector;
    private final MaestroMetrics metrics;

    public CommandIngestProcessor(MaestroProperties properties, CommandRegistry registry, CommandQueue queue,
            ChangeDetector changeDetector, MaestroMetrics metrics) {
        this.properties = properties;
        this.registry = registry;
        this.queue = queue;
        this.changeDetector = changeDetector;
        this.metrics = metrics;
    }

    @Override
    public void process(Exchange exchange) {
        String topic = exchange.getIn().getHeader(PahoMqtt5Constants.MQTT_TOPIC, String.class);
        String payload = exchange.getIn().getBody(String.class);

        String name;
        String value;
        if (properties.mqtt().payloadType() == PayloadType.JSON) {
            JsonObject json = parseJson(payload);
            if (json == null) {
                return;
            }
            name = stringValue(json, "Command");
            value = stringValue(json, "Value");
        } else {
            String suffix = topic == null ? "" : topic.substring(topic.lastIndexOf('/') + 1);
            if (suffix.isEmpty() || (payload != null && payload.trim().startsWith("{"))) {
                JsonObject json = parseJson(payload);
                if (json == null) {
                    return;
                }
                name = stringValue(json, "Command");
                value = stringValue(json, "Value");
            } else {
                name = suffix;
                value = payload == null ? "" : payload.trim();
            }
        }

        Optional<CommandDefinition> found = registry.find(name);
        if (found.isEmpty()) {
            log.warn("Ignoring unknown command '{}'", name);
            return;
        }
        CommandDefinition command = found.get();
        metrics.commandReceived();
        switch (command.type()) {
            case REFRESH -> {
                changeDetector.clear();
                log.info("Cleared the information cache, the next poll republishes all values");
            }
            case GET_INFO -> queue.put(new PendingCommand(command, "0"));
            default -> queue.put(new PendingCommand(command, value == null ? "" : value));
        }
    }

    private JsonObject parseJson(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            var parsed = JsonParser.parseString(payload);
            if (parsed.isJsonObject()) {
                return parsed.getAsJsonObject();
            }
            log.warn("Ignoring JSON command that is not an object: {}", payload);
        } catch (RuntimeException e) {
            log.warn("Ignoring malformed JSON command '{}': {}", payload, e.getMessage());
        }
        return null;
    }

    private String stringValue(JsonObject json, String key) {
        var element = json.get(key);
        if (element == null) {
            element = json.get(Character.toLowerCase(key.charAt(0)) + key.substring(1));
        }
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return null;
        }
        return element.getAsString();
    }
}
