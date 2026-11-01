package io.droidme.maestrogateway.route;

import static org.assertj.core.api.Assertions.assertThat;

import io.droidme.maestrogateway.metrics.MaestroMetrics;
import io.droidme.maestrogateway.protocol.CommandRegistry;
import io.droidme.maestrogateway.service.ChangeDetector;
import io.droidme.maestrogateway.service.CommandQueue;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.camel.Exchange;
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
        processor = new CommandIngestProcessor(new CommandRegistry(), queue, changeDetector, metrics);
    }

    @AfterEach
    void tearDown() throws Exception {
        context.close();
    }

    private Exchange exchange(String payload) {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(payload);
        return exchange;
    }

    @Test
    void readsJsonCommands() throws Exception {
        processor.process(exchange("{\"Command\":\"Power\",\"Value\":1}"));

        assertThat(queue.poll(10, TimeUnit.MILLISECONDS))
                .isNotNull()
                .satisfies(command -> {
                    assertThat(command.definition().name()).isEqualTo("Power");
                    assertThat(command.value()).isEqualTo("1");
                });
    }

    @Test
    void readsStringValues() throws Exception {
        processor.process(exchange("{\"Command\":\"Set_DateTime\",\"Value\":\"NOW\"}"));

        assertThat(queue.poll(10, TimeUnit.MILLISECONDS))
                .isNotNull()
                .satisfies(command -> assertThat(command.value()).isEqualTo("NOW"));
    }

    @Test
    void refreshClearsTheChangeCache() throws Exception {
        changeDetector.changed(Map.of("Power", 1));

        processor.process(exchange("{\"Command\":\"Refresh\",\"Value\":1}"));

        assertThat(changeDetector.size()).isZero();
        assertThat(queue.poll(10, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void ignoresUnknownCommandsAndMalformedJson() throws Exception {
        processor.process(exchange("{\"Command\":\"Nope\",\"Value\":1}"));
        processor.process(exchange("{not json"));
        processor.process(exchange("[\"Power\",1]"));

        assertThat(queue.poll(10, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void resolvesAliases() throws Exception {
        processor.process(exchange("{\"Command\":\"Diagnostics_DuctedFan1\",\"Value\":50}"));

        assertThat(queue.poll(10, TimeUnit.MILLISECONDS))
                .isNotNull()
                .satisfies(command -> assertThat(command.definition().maestroId()).isEqualTo(6));
    }
}
