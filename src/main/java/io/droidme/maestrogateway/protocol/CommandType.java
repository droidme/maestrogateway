package io.droidme.maestrogateway.protocol;

/** How a command value is transformed before it is written to the stove. */
public enum CommandType {
    /** Plain integer. */
    INT,
    /** Degrees Celsius; the stove expects half degrees, so the value is multiplied by two. */
    TEMPERATURE,
    /** Boolean; anything that is not exactly 1 is sent as 0. */
    ON_OFF,
    /** Power uses 40 for off and 1 for on. */
    ON_OFF_40,
    /** Percentage, clamped to 0..100. */
    PERCENTAGE,
    /** {@code NOW} or {@code ddMMyyyyHHmm}. */
    DATETIME,
    /** Sends {@code C|RecuperoInfo}, no value. */
    GET_INFO,
    /** Handled by the daemon (clears the change cache), never sent to the stove. */
    REFRESH
}
