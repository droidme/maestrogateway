package io.droidme.maestrogateway.service;

import io.droidme.maestrogateway.config.MaestroProperties;
import io.droidme.maestrogateway.metrics.MaestroMetrics;
import io.droidme.maestrogateway.protocol.CommandEncoder;
import io.droidme.maestrogateway.protocol.CommandRegistry;
import io.droidme.maestrogateway.protocol.MaestroMessageType;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * Persistent WebSocket client for the stove.
 *
 * <p>Uses the JDK {@link HttpClient} WebSocket implementation (there is no persistent WebSocket
 * client component in Camel 4). Complete frames are handed to a {@link FrameSink}, which feeds the
 * Camel routes. Outgoing commands are taken from the {@link CommandQueue} and encoded just before
 * they are written, so {@code NOW} date/time commands use the current time.
 */
@Component
public class MaestroWebSocketClient implements ApplicationListener<ApplicationReadyEvent>, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(MaestroWebSocketClient.class);

    private final MaestroProperties properties;
    private final FrameSink frameSink;
    private final ConnectionListener connectionListener;
    private final CommandQueue commandQueue;
    private final CommandEncoder encoder;
    private final CommandRegistry commands;
    private final MaestroMetrics metrics;

    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService sender;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean connected = new AtomicBoolean();
    private final AtomicBoolean connectScheduled = new AtomicBoolean();
    private final AtomicInteger failedReconnects = new AtomicInteger();
    private final AtomicLong lastFrameAt = new AtomicLong();

    private volatile WebSocket socket;

    public MaestroWebSocketClient(MaestroProperties properties, FrameSink frameSink,
            ConnectionListener connectionListener, CommandQueue commandQueue, CommandEncoder encoder,
            CommandRegistry commands, MaestroMetrics metrics) {
        this.properties = properties;
        this.frameSink = frameSink;
        this.connectionListener = connectionListener;
        this.commandQueue = commandQueue;
        this.encoder = encoder;
        this.commands = commands;
        this.metrics = metrics;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.stove().connectTimeout())
                .build();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> daemon(runnable, "maestro-ws-scheduler"));
        this.sender = Executors.newSingleThreadExecutor(runnable -> daemon(runnable, "maestro-ws-sender"));
    }

    private static Thread daemon(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!properties.stove().autoConnect()) {
            log.info("Stove auto-connect is disabled");
            return;
        }
        start();
    }

    public synchronized void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        MaestroProperties.Stove stove = properties.stove();
        log.info("Websocket: starting client for ws://{}:{}{}", stove.host(), stove.port(), normalizedPath());
        scheduleConnect(0);
        scheduler.scheduleWithFixedDelay(this::ping, stove.pingIntervalMs(), stove.pingIntervalMs(),
                TimeUnit.MILLISECONDS);
        long watchdogPeriod = Math.max(stove.readTimeoutMs() / 2, 1_000);
        scheduler.scheduleWithFixedDelay(this::watchdog, stove.readTimeoutMs(), watchdogPeriod,
                TimeUnit.MILLISECONDS);
        sender.submit(this::senderLoop);
    }

    public synchronized void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        connected.set(false);
        WebSocket current = socket;
        socket = null;
        if (current != null) {
            try {
                current.abort();
            } catch (Exception e) {
                log.debug("Websocket: abort failed: {}", e.toString());
            }
        }
        scheduler.shutdownNow();
        sender.shutdownNow();
        log.info("Websocket: client stopped");
    }

    @Override
    public void destroy() {
        stop();
    }

    public boolean isConnected() {
        return connected.get();
    }

    public int failedReconnects() {
        return failedReconnects.get();
    }

    /** Epoch millis of the last received frame, or 0 when no frame has been received yet. */
    public long lastFrameAt() {
        return lastFrameAt.get();
    }

    private String normalizedPath() {
        String path = properties.stove().path();
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private void scheduleConnect(long delayMs) {
        if (!running.get()) {
            return;
        }
        if (!connectScheduled.compareAndSet(false, true)) {
            return;
        }
        try {
            scheduler.schedule(() -> {
                connectScheduled.set(false);
                connect();
            }, delayMs, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            connectScheduled.set(false);
        }
    }

    private void connect() {
        if (!running.get() || socket != null) {
            return;
        }
        MaestroProperties.Stove stove = properties.stove();
        URI uri = URI.create("ws://" + stove.host() + ":" + stove.port() + normalizedPath());
        Listener listener = new Listener(uri);
        httpClient.newWebSocketBuilder()
                .connectTimeout(stove.connectTimeout())
                .buildAsync(uri, listener)
                .whenComplete((webSocket, error) -> {
                    if (error != null) {
                        onConnectFailure(uri, error);
                    }
                });
    }

    private void onConnectFailure(URI uri, Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null
                ? error.getCause()
                : error;
        int failures = failedReconnects.incrementAndGet();
        log.warn("Websocket: connection to {} failed (attempt {}): {}", uri, failures, cause.toString());
        if (failures > properties.stove().alertAfterFailedReconnects()) {
            connectionListener.onDisconnected(true);
        }
        scheduleConnect(properties.stove().reconnectDelayMs());
    }

    private void handleDisconnect() {
        if (!connected.getAndSet(false)) {
            scheduleConnect(properties.stove().reconnectDelayMs());
            return;
        }
        socket = null;
        int failures = failedReconnects.incrementAndGet();
        log.info("Websocket: disconnected (reconnect attempt {})", failures);
        if (failures > properties.stove().alertAfterFailedReconnects()) {
            connectionListener.onDisconnected(true);
        }
        scheduleConnect(properties.stove().reconnectDelayMs());
    }

    private void sendPing() {
        WebSocket current = socket;
        if (current == null || !connected.get()) {
            return;
        }
        try {
            current.sendPing(ByteBuffer.wrap(new byte[] {(byte) 0x9e}));
        } catch (Exception e) {
            log.debug("Websocket: ping failed: {}", e.toString());
        }
    }

    private void ping() {
        sendPing();
    }

    private void watchdog() {
        if (!connected.get()) {
            return;
        }
        long last = lastFrameAt.get();
        if (last == 0) {
            return;
        }
        long age = System.currentTimeMillis() - last;
        if (age > properties.stove().readTimeoutMs()) {
            log.warn("Websocket: no data for {} ms, reconnecting", age);
            WebSocket current = socket;
            handleDisconnect();
            if (current != null) {
                try {
                    current.abort();
                } catch (Exception e) {
                    log.debug("Websocket: abort failed: {}", e.toString());
                }
            }
        }
    }

    private void senderLoop() {
        long timeout = properties.stove().sendLoopTimeoutMs();
        while (running.get()) {
            try {
                if (!connected.get() || socket == null) {
                    Thread.sleep(100);
                    continue;
                }
                PendingCommand command = commandQueue.poll(timeout, TimeUnit.MILLISECONDS);
                if (command == null) {
                    continue;
                }
                if (!connected.get() || socket == null) {
                    commandQueue.put(command);
                    continue;
                }
                Optional<String> encoded = encoder.encode(command.definition(), command.value());
                if (encoded.isEmpty()) {
                    log.debug("Websocket: not sending {}={} (unsupported or invalid value)",
                            command.definition().name(), command.value());
                    continue;
                }
                try {
                    socket.sendText(encoded.get(), true).get(5, TimeUnit.SECONDS);
                    metrics.commandSent();
                    log.info("Websocket: sent {}", encoded.get());
                } catch (Exception e) {
                    log.warn("Websocket: sending '{}' failed: {}", encoded.get(), e.toString());
                    handleDisconnect();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                log.warn("Websocket: sender error: {}", e.toString());
            }
        }
    }

    private void handleFrame(String frame) {
        lastFrameAt.set(System.currentTimeMillis());
        metrics.frameReceived();
        if (frame.length() < 2) {
            return;
        }
        String type = frame.substring(0, 2);
        if (MaestroMessageType.INFO.code().equals(type)) {
            frameSink.accept(frame);
        } else if (MaestroMessageType.STRING_DATA.code().equals(type)) {
            log.info("Stove date/time acknowledged: {}", frame);
        } else {
            log.debug("Ignoring unsupported frame type {}: {}", type, frame);
        }
    }

    private final class Listener implements WebSocket.Listener {

        private final URI uri;
        private final StringBuilder buffer = new StringBuilder();

        private Listener(URI uri) {
            this.uri = uri;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            socket = webSocket;
            connected.set(true);
            failedReconnects.set(0);
            lastFrameAt.set(System.currentTimeMillis());
            log.info("Websocket: connected to {}", uri);
            connectionListener.onConnected();
            commandQueue.put(new PendingCommand(commands.getInfo(), "0"));
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String frame = buffer.toString();
                buffer.setLength(0);
                handleFrame(frame);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            log.info("Websocket: closed ({} {})", statusCode, reason);
            handleDisconnect();
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.warn("Websocket: error: {}", error.toString());
            handleDisconnect();
        }
    }
}
