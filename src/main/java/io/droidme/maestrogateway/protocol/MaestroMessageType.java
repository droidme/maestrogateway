package io.droidme.maestrogateway.protocol;

import java.util.Arrays;
import java.util.Optional;

/** First frame of every message sent by the stove. */
public enum MaestroMessageType {

    PARAMETERS("00"),
    INFO("01"),
    DATABASE("02"),
    EXTRA_PARAMETERS("03"),
    CHRONO_DAYS("04"),
    ALARMS("0A"),
    WIFI_SONDE("0B"),
    DATABASE_NAME("0D"),
    SOFTWARE_VERSION("0E"),
    STRING_DATA("AA"),
    PING("PING");

    private final String code;

    MaestroMessageType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Optional<MaestroMessageType> fromCode(String code) {
        return Arrays.stream(values()).filter(type -> type.code.equals(code)).findFirst();
    }
}
