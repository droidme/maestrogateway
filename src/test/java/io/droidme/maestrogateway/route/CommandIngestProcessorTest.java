package io.droidme.maestrogateway.route;

import static org.assertj.core.api.Assertions.assertThat;

import io.droidme.maestrogateway.TestProperties;
import io.droidme.maestrogateway.config.MaestroProperties;
import io.droidme.maestrogateway.metrics.MaestroMetrics;
import io.droidme.maestrogateway.protocol.CommandRegistry;
import io.droidme.maestrogateway.protocol.PayloadType;
import io.droidme.maestrogateway.service.ChangeDetector;
import io.droidme.maestrogateway.service.CommandQueue;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.camel.Exchange;
import org.apache.camel.component.paho.mqtt5.PahoMqtt5Constants;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CommandIngestProcessorTest {

    private final CommandQueue queue = new CommandQueue();
    private final ChangeDetector changeDetector = new ChangeDetector();
    private final DefaultCamelContext context = new DefaultCamelContext();
    private final MaestroMetrics metrics = new MaestroMetrics(new SimpleMeterRegistry());
    private CommandIngestProcessor processor;

    @BeforeEach
    void setUp() {
        usePayloadType(PayloadType.TOPIC);
    }

    private void usePayloadType(PayloadType payloadType) {
        MaestroProperties properties = TestProperties.bind(Map.of("mqtt.payload-type", payloadType.name()));
        processor = new CommandIngestProcessor(properties, new CommandRegistry(), queue, changeDetector, metrics);
    }

    @AfterEach
    void tearDown() throws Exception {
        context.close();
    }

    private Exchange exchange(String topic, String payload) {
        Exchange exchange = new DefaultExchange(context);
        if (topic != null) {
            exchange.getIn().setHeader(PahoMqtt5Constants.MQTT_TOPIC, topic);
        }
        exchange.getIn().setBody(payload);
        return exchange;
    }

    @Test
    void readsTopicCommandsInTopicMode() throws Exception {
        processor.process(exchange("Maestro/Command/Power", "1"));

        assertThat(queue.poll(10, TimeUnit.MILLISECONDS))
                .isNotNull()
                .satisfies(command -> {
                    assertThat(command.definition().name()).isEqualTo("Power");
                    assertThat(command.value()).isEqualTo("1");
                });
    }

    @Test
    void readsJsonCommandsInJsonMode() throws Exception {
        usePayloadType(PayloadType.JSON);

        processor.process(exchange("Maestro/Command",
                "{\"Command\":\"Temperature_Setpoint\",\"Value\":20.5}"));

        assertThat(queue.poll(10, TimeUnit.MILLISECONDS))
                .isNotNull()
                .satisfies(command -> {
                    assertThat(command.definition().name()).isEqualTo("Temperature_Setpoint");
                    assertThat(command.value()).isEqualTo("20.5");
                });
    }

    @Test
    void acceptsJsonOnATopicMessage() throws Exception {
        processor.process(exchange("Maestro/Command", "{\"Command\":\"Eco_Mode\",\"Value\":1}"));

        assertThat(queue.poll(10, TimeUnit.MILLISECONDS))
                .isNotNull()
                .satisfies(command -> assertThat(command.definition().name()).isEqualTo("Eco_Mode"));
    }

    @Test
    void refreshClearsTheChangeCache() throws Exception {
        changeDetector.changed(Map.of("Power", 1));

        processor.process(exchange("Maestro/Command/Refresh", "1"));

        assertThat(changeDetector.size()).isZero();
        assertThat(queue.poll(10, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void ignoresUnknownCommandsAndMalformedJson() throws Exception {
        processor.process(exchange("Maestro/Command/Nope", "1"));
        processor.process(exchange("Maestro/Command", "{not json"));

        assertThat(queue.poll(10, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void resolvesAliases() throws Exception {
        processor.process(exchange("Maestro/Command/Diagnostics_DuctedFan1", "50"));

        assertThat(queue.poll(10, TimeUnit.MILLISECONDS))
                .isNotNull()
                .satisfies(command -> assertThat(command.definition().maestroId()).isEqualTo(6));
    }
}
