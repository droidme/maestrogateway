package io.droidme.maestrogateway.protocol;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Registry of the fields inside an information frame.
 *
 * <p>The index of a field is its position in the pipe separated frame produced by
 * {@code C|RecuperoInfo}. Index 0 is the message type and is never published.
 */
@Component
public class InformationRegistry {

    private static final List<InfoField> FIELDS = List.of(
            new InfoField(0, "Messagetype", ValueType.INT),
            new InfoField(1, "Stove_State", ValueType.INT),
            new InfoField(2, "Fan_State", ValueType.INT),
            new InfoField(3, "DuctedFan1", ValueType.INT),
            new InfoField(4, "DuctedFan2", ValueType.INT),
            new InfoField(5, "Fume_Temperature", ValueType.INT),
            new InfoField(6, "Ambient_Temperature", ValueType.TEMPERATURE),
            new InfoField(7, "Puffer_Temperature", ValueType.TEMPERATURE),
            new InfoField(8, "Boiler_Temperature", ValueType.TEMPERATURE),
            new InfoField(9, "NTC3_Temperature", ValueType.TEMPERATURE),
            new InfoField(10, "Candle_Condition", ValueType.INT),
            new InfoField(11, "ACTIVE_Set", ValueType.INT),
            new InfoField(12, "RPM_Fam_Fume", ValueType.INT),
            new InfoField(13, "RPM_WormWheel_Set", ValueType.INT),
            new InfoField(14, "RPM_WormWheel_Live", ValueType.INT),
            new InfoField(15, "3WayValve", ValueType.THREE_WAY_VALVE),
            new InfoField(16, "Pump_PWM", ValueType.INT),
            new InfoField(17, "Brazier", ValueType.BRAZIER),
            new InfoField(18, "Profile", ValueType.INT),
            new InfoField(19, "Modbus_Address", ValueType.INT),
            new InfoField(20, "Active_Mode", ValueType.INT),
            new InfoField(21, "Active_Live", ValueType.INT),
            new InfoField(22, "Control_Mode", ValueType.INT),
            new InfoField(23, "Eco_Mode", ValueType.INT),
            new InfoField(24, "Silent_Mode", ValueType.INT),
            new InfoField(25, "Chronostat", ValueType.INT),
            new InfoField(26, "Temperature_Setpoint", ValueType.TEMPERATURE),
            new InfoField(27, "Boiler_Setpoint", ValueType.TEMPERATURE),
            new InfoField(28, "Temperature_Motherboard", ValueType.TEMPERATURE),
            new InfoField(29, "Power_Level", ValueType.INT),
            new InfoField(30, "FirmwareVersion", ValueType.INT),
            new InfoField(31, "DatabaseID", ValueType.INT),
            new InfoField(32, "Date_Time_Hours", ValueType.INT),
            new InfoField(33, "Date_Time_Minutes", ValueType.INT),
            new InfoField(34, "Date_Day_Of_Month", ValueType.INT),
            new InfoField(35, "Date_Month", ValueType.INT),
            new InfoField(36, "Date_Year", ValueType.INT),
            new InfoField(37, "Total_Operating_Hours", ValueType.TIMESPAN),
            new InfoField(38, "Hours_Of_Operation_In_Power1", ValueType.TIMESPAN),
            new InfoField(39, "Hours_Of_Operation_In_Power2", ValueType.TIMESPAN),
            new InfoField(40, "Hours_Of_Operation_In_Power3", ValueType.TIMESPAN),
            new InfoField(41, "Hours_Of_Operation_In_Power4", ValueType.TIMESPAN),
            new InfoField(42, "Hours_Of_Operation_In_Power5", ValueType.TIMESPAN),
            new InfoField(43, "Hours_To_Service", ValueType.INT),
            new InfoField(44, "Minutes_To_Switch_Off", ValueType.INT),
            new InfoField(45, "Number_Of_Ignitions", ValueType.INT),
            new InfoField(46, "Active_Temperature", ValueType.INT),
            new InfoField(47, "Pellet_Sensor", ValueType.INT),
            new InfoField(48, "Celcius_Or_Fahrenheit", ValueType.ON_OFF),
            new InfoField(49, "Sound_Effects", ValueType.ON_OFF),
            new InfoField(50, "Sleep", ValueType.ON_OFF),
            new InfoField(51, "Mode", ValueType.ON_OFF),
            new InfoField(52, "WifiSondeTemperature1", ValueType.INT),
            new InfoField(53, "WifiSondeTemperature2", ValueType.INT),
            new InfoField(54, "WifiSondeTemperature3", ValueType.INT),
            new InfoField(55, "Unknown", ValueType.INT),
            new InfoField(56, "SetPuffer", ValueType.INT),
            new InfoField(57, "SetBoiler", ValueType.INT),
            new InfoField(58, "SetHealth", ValueType.INT),
            new InfoField(59, "Return_Temperature", ValueType.TEMPERATURE),
            new InfoField(60, "AntiFreeze", ValueType.ON_OFF)
    );

    /**
     * Returns the field at the given frame position. Positions above 60 are reported with a
     * synthetic name so that a newer firmware does not break the parser.
     */
    public InfoField at(int index) {
        if (index >= 0 && index < FIELDS.size()) {
            return FIELDS.get(index);
        }
        return new InfoField(index, "Unknown" + index, ValueType.INT);
    }

    public Optional<InfoField> byName(String name) {
        return FIELDS.stream().filter(field -> field.name().equals(name)).findFirst();
    }

    public List<InfoField> all() {
        return FIELDS;
    }
}
