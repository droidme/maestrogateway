package io.droidme.maestrogateway.protocol;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Mapping of the numeric {@code Stove_State} value to a description and on/off semantics. */
@Component
public class StoveStateRegistry {

    private static final List<StoveState> STATES = List.of(
            new StoveState(0, "Off", false),
            new StoveState(1, "Checking hot or cold", true),
            new StoveState(2, "Cleaning cold", true),
            new StoveState(3, "Loading Pellets Cold", true),
            new StoveState(4, "Start 1 Cold", true),
            new StoveState(5, "Start 2 Cold", true),
            new StoveState(6, "Cleaning Hot", true),
            new StoveState(7, "Loading Pellets Hot", true),
            new StoveState(8, "Start 1 Hot", true),
            new StoveState(9, "Start 2 Hot", true),
            new StoveState(10, "Stabilising", true),
            new StoveState(11, "Power 1", true),
            new StoveState(12, "Power 2", true),
            new StoveState(13, "Power 3", true),
            new StoveState(14, "Power 4", true),
            new StoveState(15, "Power 5", true),
            new StoveState(30, "Diagnostics", false),
            new StoveState(31, "On", true),
            new StoveState(40, "Extinguish", true),
            new StoveState(41, "Cooling", true),
            new StoveState(42, "Cleaning Low", true),
            new StoveState(43, "Cleaning High", true),
            new StoveState(44, "UNLOCKING SCREW", false),
            new StoveState(45, "Auto Eco", false),
            new StoveState(46, "Standby", false),
            new StoveState(48, "Diagnostics", false),
            new StoveState(49, "Loading Auger", false),
            new StoveState(50, "Error A01 - Ignition failed", false),
            new StoveState(51, "Error A02 - No flame", false),
            new StoveState(52, "Error A03 - Tank overheating", false),
            new StoveState(53, "Error A04 - Flue gas temperature too high", false),
            new StoveState(54, "Error A05 - Duct obstruction - Wind", false),
            new StoveState(55, "Error A06 - Bad printing", false),
            new StoveState(56, "Error A09 - SMOKE PROBE", false),
            new StoveState(57, "Error A11 - GEAR MOTOR", false),
            new StoveState(58, "Error A13 - MOTHERBOARD TEMPERATURE", false),
            new StoveState(59, "Error A14 - DEFECT ACTIVE", false),
            new StoveState(60, "Error A18 - WATER TEMP ALARM", false),
            new StoveState(61, "Error A19 - FAULTY WATER PROBE", false),
            new StoveState(62, "Error A20 - FAILURE OF AUXILIARY PROBE", false),
            new StoveState(63, "Error A21 - PRESSURE SWITCH ALARM", false),
            new StoveState(64, "Error A22 - ROOM PROBE FAULT", false),
            new StoveState(65, "Error A23 - BRAZIL CLOSING FAULT", false),
            new StoveState(66, "Error A12 - MOTOR REDUCER CONTROLLER FAILURE", false),
            new StoveState(67, "Error A17 - ENDLESS SCREW JAM", false),
            new StoveState(69, "WAITING FOR SECURITY ALARMS", false)
    );

    private static final Map<Integer, StoveState> BY_ID = index();

    private static Map<Integer, StoveState> index() {
        Map<Integer, StoveState> map = new LinkedHashMap<>();
        for (StoveState state : STATES) {
            map.put(state.id(), state);
        }
        return Map.copyOf(map);
    }

    public Optional<StoveState> byId(int id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    /** Unknown states are treated as "off", matching the original Python gateway. */
    public boolean isOn(int id) {
        return byId(id).map(StoveState::on).orElse(false);
    }

    /** States 30 and 48 both mean the stove is in diagnostics mode. */
    public boolean isDiagnostics(int id) {
        return id == 30 || id == 48;
    }

    public List<StoveState> all() {
        return STATES;
    }
}
