package io.droidme.maestrogateway.service;

import io.droidme.maestrogateway.protocol.CommandDefinition;

/** A command waiting to be written to the stove. */
public record PendingCommand(CommandDefinition definition, String value) {
}
