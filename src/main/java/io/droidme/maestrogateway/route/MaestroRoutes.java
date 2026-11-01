package io.droidme.maestrogateway.route;

import io.droidme.maestrogateway.config.MaestroProperties;
import io.droidme.maestrogateway.protocol.CommandRegistry;
import io.droidme.maestrogateway.service.CommandQueue;
import io.droidme.maestrogateway.service.MaestroWebSocketClient;
import io.droidme.maestrogateway.service.MqttPublisher;
import io.droidme.maestrogateway.service.MqttTopics;
import io.droidme.maestrogateway.service.PendingCommand;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * Camel routes of the gateway.
 *
 * <ul>
 *   <li>{@code stove-frame}: parse a frame, drop unchanged values, publish the rest.</li>
 *   <li>{@code mqtt-command}: MQTT subscription for commands (started by {@code MqttRouteSupervisor}).</li>
 *   <li>{@code stove-poll}: request fresh information every poll interval.</li>
 * </ul>
 */
@Component
public class MaestroRoutes extends RouteBuilder {

    public static final String FRAME_ENDPOINT = "direct:maestroFrame";
    public static final String MQTT_COMMAND_ROUTE_ID = "mqtt-command";

    private final MaestroProperties properties;
    private final MqttTopics topics;
    private final FrameProcessor frameProcessor;
    private final ChangeFilterProcessor changeFilterProcessor;
    private final InfoPublishProcessor infoPublishProcessor;
    private final CommandIngestProcessor commandIngestProcessor;
    private final MaestroWebSocketClient webSocketClient;
    private final CommandQueue commandQueue;
    private final CommandRegistry commandRegistry;
    private final MqttPublisher mqttPublisher;

    public MaestroRoutes(MaestroProperties properties, MqttTopics topics, FrameProcessor frameProcessor,
            ChangeFilterProcessor changeFilterProcessor, InfoPublishProcessor infoPublishProcessor,
            CommandIngestProcessor commandIngestProcessor, MaestroWebSocketClient webSocketClient,
            CommandQueue commandQueue, CommandRegistry commandRegistry, MqttPublisher mqttPublisher) {
        this.properties = properties;
        this.topics = topics;
        this.frameProcessor = frameProcessor;
        this.changeFilterProcessor = changeFilterProcessor;
        this.infoPublishProcessor = infoPublishProcessor;
        this.commandIngestProcessor = commandIngestProcessor;
        this.webSocketClient = webSocketClient;
        this.commandQueue = commandQueue;
        this.commandRegistry = commandRegistry;
        this.mqttPublisher = mqttPublisher;
    }

    @Override
    public void configure() {
        from(FRAME_ENDPOINT)
                .routeId("stove-frame")
                .process(frameProcessor)
                .filter(body().isNotNull())
                .process(changeFilterProcessor)
                .filter(body().isNotNull())
                .process(infoPublishProcessor);

        if (properties.mqtt().enabled()) {
            String clientId = URLEncoder.encode(
                    properties.mqtt().clientId() + "-sub", StandardCharsets.UTF_8);
            from("paho-mqtt5:" + topics.commandTopic() + "?clientId=" + clientId
                    + "&qos=" + properties.mqtt().qos())
                    .routeId(MQTT_COMMAND_ROUTE_ID)
                    .autoStartup(false)
                    .process(commandIngestProcessor);
        }

        from("timer:stovePoll?period=" + properties.stove().pollIntervalMs()
                + "&delay=1000&fixedRate=true")
                .routeId("stove-poll")
                .filter(exchange -> webSocketClient.isConnected())
                .process(exchange -> commandQueue.put(new PendingCommand(commandRegistry.getInfo(), "0")))
                .process(exchange -> mqttPublisher.publishState("ON"));
    }
}
