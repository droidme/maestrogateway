package io.droidme.maestrogateway.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.droidme.maestrogateway.protocol.CommandDefinition;
import io.droidme.maestrogateway.protocol.CommandRegistry;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class CommandQueueTest {

    private final CommandRegistry registry = new CommandRegistry();

    private PendingCommand command(String name, String value) {
        CommandDefinition definition = registry.find(name).orElseThrow();
        return new PendingCommand(definition, value);
    }

    @Test
    void replacesTheValueOfAPendingCommandAndKeepsItsPosition() throws Exception {
        CommandQueue queue = new CommandQueue();
        queue.put(command("Power", "0"));
        queue.put(command("Eco_Mode", "1"));
        queue.put(command("Power", "1"));

        assertThat(queue.size()).isEqualTo(2);
        assertThat(queue.poll(10, TimeUnit.MILLISECONDS))
                .isEqualTo(command("Power", "1"));
        assertThat(queue.poll(10, TimeUnit.MILLISECONDS))
                .isEqualTo(command("Eco_Mode", "1"));
    }

    @Test
    void returnsNullWhenNothingIsPending() throws Exception {
        CommandQueue queue = new CommandQueue();
        assertThat(queue.poll(20, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void clearsAllPendingCommands() {
        CommandQueue queue = new CommandQueue();
        queue.put(command("Power", "1"));
        queue.clear();
        assertThat(queue.size()).isZero();
    }
}
