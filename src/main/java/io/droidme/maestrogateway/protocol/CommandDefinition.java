package io.droidme.maestrogateway.protocol;

/**
 * A command that can be sent to the stove.
 *
 * @param name      name used on the MQTT command topic
 * @param maestroId parameter id understood by the stove
 * @param type      value transformation
 * @param category  basic, diagnostics, date/time or daemon
 */
public record CommandDefinition(String name, int maestroId, CommandType type, CommandCategory category) {
}
