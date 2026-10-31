package io.droidme.maestrogateway.protocol;

/** How MQTT payloads are published and commands are received. */
public enum PayloadType {
    /** One topic per value, e.g. {@code Maestro/Ambient_Temperature}. */
    TOPIC,
    /** A single JSON object on the publish prefix, e.g. {@code Maestro}. */
    JSON
}
