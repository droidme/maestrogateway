package io.droidme.maestrogateway.service;

/** Callbacks for WebSocket connection state changes. */
public interface ConnectionListener {

    void onConnected();

    /**
     * Called when the connection is lost.
     *
     * @param alert {@code true} once enough reconnect attempts failed that a status message should
     *              be published
     */
    void onDisconnected(boolean alert);
}
