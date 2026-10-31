package io.droidme.maestrogateway.service;

/** Receives complete text frames from the stove WebSocket. */
@FunctionalInterface
public interface FrameSink {

    void accept(String frame);
}
