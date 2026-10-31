package io.droidme.maestrogateway.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import io.droidme.maestrogateway.metrics.MaestroMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InfoFrameParserTest {

    private InfoFrameParser parser;

    @BeforeEach
    void setUp() {
        parser = new InfoFrameParser(new InformationRegistry(), new StoveStateRegistry(),
                new MaestroMetrics(new SimpleMeterRegistry()));
    }

    @Test
    void parsesTemperaturesStatesAndSpecialFields() {
        int[] values = new int[60];
        values[0] = 11;          // Stove_State: Power 1
        values[1] = 1;           // Fan_State
        values[4] = 155;         // Fume_Temperature (raw, not halved)
        values[5] = 47;          // Ambient_Temperature -> 23.5
        values[6] = 255;         // Puffer_Temperature -> 127.5
        values[7] = 125;         // Boiler_Temperature -> 62.5
        values[8] = 255;         // NTC3_Temperature -> 127.5
        values[14] = 1;          // 3WayValve -> Sani
        values[16] = 0;          // Brazier -> OK
        values[17] = 0;          // Profile
        values[18] = 11;         // Modbus_Address
        values[19] = 1;          // Active_Mode
        values[20] = 157;        // Active_Live
        values[21] = 1;          // Control_Mode
        values[23] = 1;          // Silent_Mode
        values[25] = 47;         // Temperature_Setpoint -> 23.5
        values[26] = 160;        // Boiler_Setpoint -> 80.0
        values[27] = 75;         // Temperature_Motherboard -> 37.5
        values[28] = 11;         // Power_Level
        values[36] = 1_105_203;  // Total_Operating_Hours -> 307:00:03

        Map<String, Object> result = parser.parse(FrameFixtures.frame(values));

        assertThat(result)
                .containsEntry("Stove_State", 11)
                .containsEntry("Power", 1)
                .containsEntry("Diagnostics", 0)
                .containsEntry("Fume_Temperature", 155)
                .containsEntry("Ambient_Temperature", 23.5)
                .containsEntry("Puffer_Temperature", 127.5)
                .containsEntry("Boiler_Temperature", 62.5)
                .containsEntry("3WayValve", "Sani")
                .containsEntry("Brazier", "OK")
                .containsEntry("Total_Operating_Hours", "307:00:03")
                .containsEntry("Boiler_Setpoint", 80.0)
                .containsEntry("Temperature_Motherboard", 37.5);
    }

    @Test
    void derivesDiagnosticsAndPowerFromStoveState() {
        int[] values = new int[2];
        values[0] = 30; // Diagnostics

        Map<String, Object> result = parser.parse(FrameFixtures.frame(values));

        assertThat(result).containsEntry("Stove_State", 30)
                .containsEntry("Power", 0)
                .containsEntry("Diagnostics", 1);
    }

    @Test
    void mapsRiscAndClrValues() {
        int[] values = new int[17];
        values[14] = 0; // 3WayValve -> Risc
        values[16] = 1; // Brazier -> CLR

        Map<String, Object> result = parser.parse(FrameFixtures.frame(values));

        assertThat(result).containsEntry("3WayValve", "Risc")
                .containsEntry("Brazier", "CLR");
    }

    @Test
    void skipsInvalidTokensWithoutFailingTheFrame() {
        Map<String, Object> result = parser.parse("01|0B|01|ZZ|00|9B");

        assertThat(result).containsEntry("Stove_State", 11)
                .containsEntry("Fan_State", 1)
                .containsEntry("DuctedFan2", 0)
                .containsEntry("Fume_Temperature", 155)
                .doesNotContainKey("DuctedFan1");
    }

    @Test
    void parsesShortFrames() {
        Map<String, Object> result = parser.parse("01|0B");

        assertThat(result).containsEntry("Stove_State", 11)
                .containsEntry("Power", 1);
    }

    @Test
    void ignoresNonInformationFramesAndBlankInput() {
        assertThat(parser.parse("AA|251220261636")).isEmpty();
        assertThat(parser.parse("")).isEmpty();
        assertThat(parser.parse(null)).isEmpty();
    }

    @Test
    void namesFieldsBeyondTheKnownRange() {
        StringBuilder frame = new StringBuilder("01");
        for (int i = 0; i < 62; i++) {
            frame.append("|00");
        }
        frame.append("|0F"); // index 63

        Map<String, Object> result = parser.parse(frame.toString());

        assertThat(result).containsEntry("Unknown62", 0)
                .containsEntry("Unknown63", 15);
    }

    @Test
    void formatsTimespans() {
        assertThat(InfoFrameParser.formatTimespan(0)).isEqualTo("0:00:00");
        assertThat(InfoFrameParser.formatTimespan(3_661)).isEqualTo("1:01:01");
        assertThat(InfoFrameParser.formatTimespan(1_105_203)).isEqualTo("307:00:03");
    }
}
