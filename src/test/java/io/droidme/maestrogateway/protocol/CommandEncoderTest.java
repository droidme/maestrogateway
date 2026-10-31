package io.droidme.maestrogateway.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CommandEncoderTest {

    private final CommandRegistry registry = new CommandRegistry();
    private final CommandEncoder encoder = new CommandEncoder();

    private String encode(String name, String value) {
        return encoder.encode(registry.find(name).orElseThrow(), value).orElseThrow();
    }

    @Test
    void encodesTemperatureAsHalfDegrees() {
        assertThat(encode("Temperature_Setpoint", "20.5")).isEqualTo("C|WriteParametri|42|41");
        assertThat(encode("Temperature_Setpoint", "21")).isEqualTo("C|WriteParametri|42|42");
        assertThat(encode("Boiler_Setpoint", "62.5")).isEqualTo("C|WriteParametri|51|125");
        assertThat(encode("Chronostat_T1", "19")).isEqualTo("C|WriteParametri|1108|38");
    }

    @Test
    void encodesPowerWithFortyForOff() {
        assertThat(encode("Power", "1")).isEqualTo("C|WriteParametri|34|1");
        assertThat(encode("Power", "0")).isEqualTo("C|WriteParametri|34|40");
        assertThat(encode("Power", "ON")).isEqualTo("C|WriteParametri|34|1");
        assertThat(encode("Power", "true")).isEqualTo("C|WriteParametri|34|1");
        assertThat(encode("Power", "5")).isEqualTo("C|WriteParametri|34|1");
        assertThat(encode("Power", "OFF")).isEqualTo("C|WriteParametri|34|40");
    }

    @Test
    void encodesOnOffChildren() {
        assertThat(encode("Eco_Mode", "1")).isEqualTo("C|WriteParametri|41|1");
        assertThat(encode("Eco_Mode", "0")).isEqualTo("C|WriteParametri|41|0");
        assertThat(encode("Eco_Mode", "7")).isEqualTo("C|WriteParametri|41|0");
    }

    @Test
    void clampsPercentagesAndUsesDiagnosticsPrefix() {
        assertThat(encode("FrontFan", "120")).isEqualTo("C|Diagnostica|5|100");
        assertThat(encode("FrontFan", "-5")).isEqualTo("C|Diagnostica|5|0");
        assertThat(encode("Pump_PWM", "50")).isEqualTo("C|Diagnostica|8|50");
        assertThat(encode("Diagnostics", "1")).isEqualTo("C|Diagnostica|100|1");
        assertThat(encode("3wayvalve", "1")).isEqualTo("C|Diagnostica|9|1");
    }

    @Test
    void encodesInfoRequest() {
        assertThat(encode("GetInfo", "0")).isEqualTo("C|RecuperoInfo");
    }

    @Test
    void encodesDateTimeAndValidatesInput() {
        assertThat(encode("Set_DateTime", "251220261636")).isEqualTo("C|SalvaDataOra|251220261636");
        assertThat(encode("Set_DateTime", "NOW")).matches("C\\|SalvaDataOra\\|\\d{12}");
    }

    @Test
    void rejectsInvalidValues() {
        assertThat(encoder.encode(registry.find("Set_DateTime").orElseThrow(), "25122026")).isEmpty();
        assertThat(encoder.encode(registry.find("Set_DateTime").orElseThrow(), "321320261636")).isEmpty();
        assertThat(encoder.encode(registry.find("Power").orElseThrow(), "abc")).isEmpty();
        assertThat(encoder.encode(registry.find("Power").orElseThrow(), "")).isEmpty();
        assertThat(encoder.encode(registry.find("Refresh").orElseThrow(), "0")).isEmpty();
    }

    @Test
    void encodesResetCommands() {
        assertThat(encode("Reset_Alarm", "255")).isEqualTo("C|WriteParametri|1|255");
        assertThat(encode("Reset_Active", "255")).isEqualTo("C|WriteParametri|2|255");
    }
}
