package io.droidme.maestrogateway.protocol;

/** One stove state, as reported in {@code Stove_State}. */
public record StoveState(int id, String description, boolean on) {
}
