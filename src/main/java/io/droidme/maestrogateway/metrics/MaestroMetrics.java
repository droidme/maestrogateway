package io.droidme.maestrogateway.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** Counters for the gateway. Gauges are registered in {@link MetricsConfig}. */
@Component
public class MaestroMetrics {

    private final Counter framesReceived;
    private final Counter frameParseErrors;
    private final Counter commandsReceived;
    private final Counter commandsSent;
    private final Counter publishSuccesses;
    private final Counter publishFailures;

    public MaestroMetrics(MeterRegistry registry) {
        this.framesReceived = Counter.builder("maestro.frames.received")
                .description("Information frames received from the stove")
                .register(registry);
        this.frameParseErrors = Counter.builder("maestro.frames.parse.errors")
                .description("Information frame tokens that could not be parsed")
                .register(registry);
        this.commandsReceived = Counter.builder("maestro.commands.received")
                .description("Commands received over MQTT")
                .register(registry);
        this.commandsSent = Counter.builder("maestro.commands.sent")
                .description("Commands successfully written to the stove")
                .register(registry);
        this.publishSuccesses = Counter.builder("maestro.mqtt.publish.success")
                .description("MQTT messages published successfully")
                .register(registry);
        this.publishFailures = Counter.builder("maestro.mqtt.publish.failures")
                .description("MQTT publish attempts that failed")
                .register(registry);
    }

    public void frameReceived() {
        framesReceived.increment();
    }

    public void frameParseError() {
        frameParseErrors.increment();
    }

    public void commandReceived() {
        commandsReceived.increment();
    }

    public void commandSent() {
        commandsSent.increment();
    }

    public void publishSuccess() {
        publishSuccesses.increment();
    }

    public void publishFailure() {
        publishFailures.increment();
    }
}
