package io.droidme.maestrogateway.protocol;

/** One field of the information frame ("RecuperoInfo"). */
public record InfoField(int index, String name, ValueType type) {
}
