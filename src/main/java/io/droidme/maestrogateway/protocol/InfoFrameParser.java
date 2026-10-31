package io.droidme.maestrogateway.protocol;

import io.droidme.maestrogateway.metrics.MaestroMetrics;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Parses an information frame ({@code 01|...}) into a map of field name to value.
 *
 * <p>Frames are pipe separated hexadecimal tokens. Temperatures are raw value divided by two,
 * timespans are seconds rendered as {@code H:MM:SS}, and a few fields have textual semantics
 * (3-way valve, brazier). Invalid tokens are skipped instead of failing the whole frame: a stove
 * firmware update must never take the gateway down.
 */
@Component
public class InfoFrameParser {

    private static final Logger log = LoggerFactory.getLogger(InfoFrameParser.class);

    private final InformationRegistry registry;
    private final StoveStateRegistry stoveStates;
    private final MaestroMetrics metrics;

    public InfoFrameParser(InformationRegistry registry, StoveStateRegistry stoveStates, MaestroMetrics metrics) {
        this.registry = registry;
        this.stoveStates = stoveStates;
        this.metrics = metrics;
    }

    public Map<String, Object> parse(String frame) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (frame == null || frame.isBlank()) {
            return result;
        }
        String[] parts = frame.split("\\|", -1);
        if (!MaestroMessageType.INFO.code().equals(parts[0].trim())) {
            return result;
        }
        for (int index = 1; index < parts.length; index++) {
            String token = parts[index].trim();
            if (token.isEmpty()) {
                continue;
            }
            InfoField field = registry.at(index);
            Integer raw = parseHex(token, field);
            if (raw == null) {
                continue;
            }
            result.put(field.name(), toValue(field.type(), raw));
            if ("Stove_State".equals(field.name())) {
                result.put("Power", stoveStates.isOn(raw) ? 1 : 0);
                result.put("Diagnostics", stoveStates.isDiagnostics(raw) ? 1 : 0);
            }
        }
        return result;
    }

    private Integer parseHex(String token, InfoField field) {
        try {
            return Integer.parseInt(token, 16);
        } catch (NumberFormatException e) {
            metrics.frameParseError();
            log.warn("Ignoring unparsable token '{}' for field {} at index {}", token, field.name(), field.index());
            return null;
        }
    }

    private Object toValue(ValueType type, int raw) {
        return switch (type) {
            case TEMPERATURE -> raw / 2.0;
            case TIMESPAN -> formatTimespan(raw);
            case THREE_WAY_VALVE -> raw == 1 ? "Sani" : "Risc";
            case BRAZIER -> raw == 0 ? "OK" : "CLR";
            case INT, ON_OFF -> raw;
        };
    }

    static String formatTimespan(long seconds) {
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        long hours = minutes / 60;
        long remainingMinutes = minutes % 60;
        return String.format("%d:%02d:%02d", hours, remainingMinutes, remainingSeconds);
    }
}
