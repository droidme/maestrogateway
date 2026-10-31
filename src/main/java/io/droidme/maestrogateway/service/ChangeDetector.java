package io.droidme.maestrogateway.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Tracks the last published value of every field and returns only what changed.
 *
 * <p>On the first poll every field is reported. Afterwards a field is reported only when its value
 * differs from the cached one. {@code Refresh} clears the cache so that the next poll republishes
 * everything.
 */
@Component
public class ChangeDetector {

    private final Map<String, Object> cache = new LinkedHashMap<>();

    public synchronized Map<String, Object> changed(Map<String, Object> values) {
        Map<String, Object> changed = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            boolean known = cache.containsKey(key);
            if (!known || !Objects.equals(cache.get(key), value)) {
                cache.put(key, value);
                changed.put(key, value);
            }
        }
        return changed;
    }

    public synchronized void clear() {
        cache.clear();
    }

    public synchronized int size() {
        return cache.size();
    }
}
