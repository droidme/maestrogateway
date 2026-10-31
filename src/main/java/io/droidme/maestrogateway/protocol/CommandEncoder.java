package io.droidme.maestrogateway.protocol;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Translates a command plus a textual value into the string written to the stove WebSocket.
 *
 * <p>Examples: {@code Temperature_Setpoint=20.5 -> C|WriteParametri|42|41} and
 * {@code Diagnostics=1 -> C|Diagnostica|100|1}.
 */
@Component
public class CommandEncoder {

    private static final Logger log = LoggerFactory.getLogger(CommandEncoder.class);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("ddMMyyyyHHmm");
    private static final Pattern TWELVE_DIGITS = Pattern.compile("\\d{12}");

    public Optional<String> encode(CommandDefinition command, String rawValue) {
        return switch (command.type()) {
            case GET_INFO -> Optional.of("C|RecuperoInfo");
            case REFRESH -> Optional.empty();
            case DATETIME -> encodeDateTime(rawValue);
            default -> encodeWrite(command, rawValue);
        };
    }

    private Optional<String> encodeDateTime(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return Optional.empty();
        }
        String value = rawValue.trim();
        if (!"NOW".equalsIgnoreCase(value) && !TWELVE_DIGITS.matcher(value).matches()) {
            log.warn("Ignoring Set_DateTime value '{}': expected NOW or ddMMyyyyHHmm", rawValue);
            return Optional.empty();
        }
        if (!"NOW".equalsIgnoreCase(value)) {
            try {
                LocalDateTime.parse(value, DATE_TIME);
            } catch (DateTimeParseException e) {
                log.warn("Ignoring Set_DateTime value '{}': not a valid date/time", rawValue);
                return Optional.empty();
            }
        } else {
            value = LocalDateTime.now().format(DATE_TIME);
        }
        return Optional.of("C|SalvaDataOra|" + value);
    }

    private Optional<String> encodeWrite(CommandDefinition command, String rawValue) {
        Double number = parseNumber(rawValue);
        if (number == null) {
            log.warn("Ignoring command {} with non-numeric value '{}'", command.name(), rawValue);
            return Optional.empty();
        }
        long encoded = switch (command.type()) {
            case TEMPERATURE -> (long) (number * 2);
            case ON_OFF_40 -> number == 0 ? 40 : 1;
            case ON_OFF -> number == 1 ? 1 : 0;
            case PERCENTAGE -> (long) Math.max(0, Math.min(100, number));
            case INT -> (long) (double) number;
            default -> 0;
        };
        String prefix = command.category() == CommandCategory.DIAGNOSTICS
                ? "C|Diagnostica|"
                : "C|WriteParametri|";
        return Optional.of(prefix + command.maestroId() + "|" + encoded);
    }

    private Double parseNumber(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String value = rawValue.trim();
        if (value.isEmpty()) {
            return null;
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "on", "true", "yes" -> 1.0;
            case "off", "false", "no" -> 0.0;
            default -> {
                try {
                    yield Double.parseDouble(value);
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
        };
    }
}
