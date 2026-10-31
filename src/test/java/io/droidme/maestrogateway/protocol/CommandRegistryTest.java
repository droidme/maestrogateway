package io.droidme.maestrogateway.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CommandRegistryTest {

    private final CommandRegistry registry = new CommandRegistry();

    @Test
    void looksUpCommandsCaseInsensitively() {
        assertThat(registry.find("power")).hasValueSatisfying(command ->
                assertThat(command.maestroId()).isEqualTo(34));
        assertThat(registry.find("POWER_LEVEL")).hasValueSatisfying(command ->
                assertThat(command.maestroId()).isEqualTo(36));
    }

    @Test
    void keepsTheBasicFanCommandWhenTheDiagnosticsOneHasTheSameName() {
        assertThat(registry.find("DuctedFan1")).hasValueSatisfying(command -> {
            assertThat(command.maestroId()).isEqualTo(38);
            assertThat(command.category()).isEqualTo(CommandCategory.BASIC);
        });
    }

    @Test
    void exposesDiagnosticsFanCommandsUnderPrefixedAliases() {
        assertThat(registry.find("Diagnostics_DuctedFan1")).hasValueSatisfying(command -> {
            assertThat(command.maestroId()).isEqualTo(6);
            assertThat(command.type()).isEqualTo(CommandType.PERCENTAGE);
            assertThat(command.category()).isEqualTo(CommandCategory.DIAGNOSTICS);
        });
    }

    @Test
    void supportsAlternativeSpellings() {
        assertThat(registry.find("Celcius_Fahrenheit")).hasValueSatisfying(command ->
                assertThat(command.maestroId()).isEqualTo(49));
        assertThat(registry.find("3WayValve")).hasValueSatisfying(command ->
                assertThat(command.maestroId()).isEqualTo(9));
    }

    @Test
    void returnsEmptyForUnknownOrBlankNames() {
        assertThat(registry.find("NoSuchCommand")).isEmpty();
        assertThat(registry.find("")).isEmpty();
        assertThat(registry.find(null)).isEmpty();
    }
}
