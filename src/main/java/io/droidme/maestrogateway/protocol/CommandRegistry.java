package io.droidme.maestrogateway.protocol;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Registry of all commands understood by the gateway.
 *
 * <p>The names match the original Python gateway so existing MQTT automations keep working. A few
 * diagnostics commands share a name with basic commands upstream (for example {@code DuctedFan1});
 * here the basic command keeps the short name and the diagnostics variant is available under
 * {@code Diagnostics_DuctedFan1}.
 */
@Component
public class CommandRegistry {

    private static final Logger log = LoggerFactory.getLogger(CommandRegistry.class);

    private final Map<String, CommandDefinition> commands = new LinkedHashMap<>();
    private final Map<String, CommandDefinition> aliases = new LinkedHashMap<>();

    public CommandRegistry() {
        // Daemon control
        register(new CommandDefinition("Refresh", 0, CommandType.REFRESH, CommandCategory.DAEMON));
        register(new CommandDefinition("GetInfo", 0, CommandType.GET_INFO, CommandCategory.DAEMON));

        // Basic commands
        register(new CommandDefinition("Temperature_Setpoint", 42, CommandType.TEMPERATURE, CommandCategory.BASIC));
        register(new CommandDefinition("Boiler_Setpoint", 51, CommandType.TEMPERATURE, CommandCategory.BASIC));
        register(new CommandDefinition("Chronostat", 1111, CommandType.ON_OFF, CommandCategory.BASIC));
        register(new CommandDefinition("Chronostat_T1", 1108, CommandType.TEMPERATURE, CommandCategory.BASIC));
        register(new CommandDefinition("Chronostat_T2", 1109, CommandType.TEMPERATURE, CommandCategory.BASIC));
        register(new CommandDefinition("Chronostat_T3", 1110, CommandType.TEMPERATURE, CommandCategory.BASIC));
        register(new CommandDefinition("Power_Level", 36, CommandType.INT, CommandCategory.BASIC));
        register(new CommandDefinition("Silent_Mode", 45, CommandType.ON_OFF, CommandCategory.BASIC));
        register(new CommandDefinition("Active_Mode", 35, CommandType.ON_OFF, CommandCategory.BASIC));
        register(new CommandDefinition("Eco_Mode", 41, CommandType.ON_OFF, CommandCategory.BASIC));
        register(new CommandDefinition("Sound_Effects", 50, CommandType.ON_OFF, CommandCategory.BASIC));
        register(new CommandDefinition("Power", 34, CommandType.ON_OFF_40, CommandCategory.BASIC));
        register(new CommandDefinition("Fan_State", 37, CommandType.INT, CommandCategory.BASIC));
        register(new CommandDefinition("DuctedFan1", 38, CommandType.INT, CommandCategory.BASIC));
        register(new CommandDefinition("DuctedFan2", 39, CommandType.INT, CommandCategory.BASIC));
        register(new CommandDefinition("Control_Mode", 40, CommandType.ON_OFF, CommandCategory.BASIC));
        register(new CommandDefinition("Profile", 149, CommandType.INT, CommandCategory.BASIC));
        register(new CommandDefinition("Feeding_Screw", 34, CommandType.INT, CommandCategory.BASIC));
        register(new CommandDefinition("Celsius_Fahrenheit", 49, CommandType.INT, CommandCategory.BASIC));
        register(new CommandDefinition("Sleep", 57, CommandType.INT, CommandCategory.BASIC));
        register(new CommandDefinition("Summer_Mode", 58, CommandType.ON_OFF, CommandCategory.BASIC));
        register(new CommandDefinition("Pellet_Sensor", 148, CommandType.ON_OFF, CommandCategory.BASIC));
        register(new CommandDefinition("Adaptive_Mode", 149, CommandType.ON_OFF, CommandCategory.BASIC));
        register(new CommandDefinition("AntiFreeze", 154, CommandType.INT, CommandCategory.BASIC));
        register(new CommandDefinition("Reset_Active", 2, CommandType.INT, CommandCategory.BASIC));
        register(new CommandDefinition("Reset_Alarm", 1, CommandType.INT, CommandCategory.BASIC));

        // Diagnostics (only accepted while the stove is powered off and in diagnostics mode)
        register(new CommandDefinition("Diagnostics", 100, CommandType.ON_OFF, CommandCategory.DIAGNOSTICS));
        register(new CommandDefinition("RPM_Fam_Fume", 1, CommandType.INT, CommandCategory.DIAGNOSTICS));
        register(new CommandDefinition("RPM_WormWheel", 2, CommandType.INT, CommandCategory.DIAGNOSTICS));
        register(new CommandDefinition("Active", 3, CommandType.INT, CommandCategory.DIAGNOSTICS));
        register(new CommandDefinition("Ignitor", 4, CommandType.ON_OFF, CommandCategory.DIAGNOSTICS));
        register(new CommandDefinition("FrontFan", 5, CommandType.PERCENTAGE, CommandCategory.DIAGNOSTICS));
        register(new CommandDefinition("DuctedFan1", 6, CommandType.PERCENTAGE, CommandCategory.DIAGNOSTICS));
        register(new CommandDefinition("DuctedFan2", 7, CommandType.PERCENTAGE, CommandCategory.DIAGNOSTICS));
        register(new CommandDefinition("Pump_PWM", 8, CommandType.PERCENTAGE, CommandCategory.DIAGNOSTICS));
        register(new CommandDefinition("3wayvalve", 9, CommandType.ON_OFF, CommandCategory.DIAGNOSTICS));

        // Date/time
        register(new CommandDefinition("Set_DateTime", 0, CommandType.DATETIME, CommandCategory.DATETIME));

        alias("Diagnostics_DuctedFan1",
                new CommandDefinition("Diagnostics_DuctedFan1", 6, CommandType.PERCENTAGE, CommandCategory.DIAGNOSTICS));
        alias("Diagnostics_DuctedFan2",
                new CommandDefinition("Diagnostics_DuctedFan2", 7, CommandType.PERCENTAGE, CommandCategory.DIAGNOSTICS));
        alias("Diagnostics_3WayValve", commands.get("3wayvalve"));
        alias("3WayValve", commands.get("3wayvalve"));
        alias("Celcius_Fahrenheit", commands.get("Celsius_Fahrenheit"));
    }

    private void register(CommandDefinition command) {
        if (commands.putIfAbsent(command.name(), command) != null) {
            log.debug("Ignoring duplicate command name {} (keeping the first definition)", command.name());
        }
    }

    private void alias(String alias, CommandDefinition command) {
        if (command != null) {
            aliases.put(alias, command);
        }
    }

    /** Looks up a command by name, case-insensitively, including aliases. */
    public Optional<CommandDefinition> find(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        CommandDefinition exact = commands.get(name);
        if (exact != null) {
            return Optional.of(exact);
        }
        CommandDefinition alias = aliases.get(name);
        if (alias != null) {
            return Optional.of(alias);
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return commands.values().stream()
                .filter(command -> command.name().toLowerCase(Locale.ROOT).equals(lower))
                .findFirst()
                .or(() -> aliases.entrySet().stream()
                        .filter(entry -> entry.getKey().toLowerCase(Locale.ROOT).equals(lower))
                        .map(Map.Entry::getValue)
                        .findFirst());
    }

    public CommandDefinition getInfo() {
        return commands.get("GetInfo");
    }

    /** All primary commands (aliases excluded). */
    public List<CommandDefinition> all() {
        return List.copyOf(commands.values());
    }
}
