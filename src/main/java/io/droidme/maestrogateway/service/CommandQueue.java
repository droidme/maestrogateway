package io.droidme.maestrogateway.service;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Component;

/**
 * De-duplicating command queue (debounce).
 *
 * <p>If a command for the same field is already pending, its value is replaced instead of adding a
 * second entry. This prevents rapid MQTT updates (for example a temperature slider) from flooding
 * the stove, and mirrors the {@code SetQueue} of the original Python implementation.
 */
@Component
public class CommandQueue {

    private final Map<String, PendingCommand> pending = new LinkedHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();

    public void put(PendingCommand command) {
        lock.lock();
        try {
            pending.put(command.definition().name(), command);
            notEmpty.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the oldest pending command, waiting up to the given timeout.
     *
     * @return the command, or {@code null} when the timeout elapsed
     */
    public PendingCommand poll(long timeout, TimeUnit unit) throws InterruptedException {
        lock.lockInterruptibly();
        try {
            long remainingNanos = unit.toNanos(timeout);
            while (pending.isEmpty()) {
                if (remainingNanos <= 0) {
                    return null;
                }
                remainingNanos = notEmpty.awaitNanos(remainingNanos);
            }
            Iterator<Map.Entry<String, PendingCommand>> iterator = pending.entrySet().iterator();
            PendingCommand command = iterator.next().getValue();
            iterator.remove();
            return command;
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return pending.size();
        } finally {
            lock.unlock();
        }
    }

    public void clear() {
        lock.lock();
        try {
            pending.clear();
        } finally {
            lock.unlock();
        }
    }
}
