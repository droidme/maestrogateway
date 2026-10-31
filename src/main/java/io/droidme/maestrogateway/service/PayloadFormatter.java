package io.droidme.maestrogateway.service;

import java.math.BigDecimal;
import java.util.Locale;

/** Formats field values for MQTT payloads in a stable, human friendly way. */
public final class PayloadFormatter {

    private PayloadFormatter() {
    }

    public static String format(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Double number) {
            if (number.isNaN() || number.isInfinite()) {
                return number.toString();
            }
            if (number == Math.rint(number) && Math.abs(number) < 1e15) {
                return String.format(Locale.ROOT, "%.1f", number);
            }
            return BigDecimal.valueOf(number).stripTrailingZeros().toPlainString();
        }
        return String.valueOf(value);
    }
}
