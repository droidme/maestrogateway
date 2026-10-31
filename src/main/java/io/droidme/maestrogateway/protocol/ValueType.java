package io.droidme.maestrogateway.protocol;

/** Wire representation of a value inside an information frame. */
public enum ValueType {
    /** Raw integer, published as-is. */
    INT,
    /** Raw value divided by two, e.g. {@code 0x2F -> 23.5}. */
    TEMPERATURE,
    /** Seconds since start, rendered as {@code H:MM:SS}. */
    TIMESPAN,
    /** {@code 1 -> "Sani"}, everything else {@code "Risc"}. */
    THREE_WAY_VALVE,
    /** {@code 0 -> "OK"}, everything else {@code "CLR"}. */
    BRAZIER,
    /** Raw integer, semantically on/off. */
    ON_OFF
}
