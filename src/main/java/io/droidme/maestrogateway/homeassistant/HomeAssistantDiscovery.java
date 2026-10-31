package io.droidme.maestrogateway.homeassistant;

import io.droidme.maestrogateway.config.MaestroProperties;
import io.droidme.maestrogateway.service.MqttPublisher;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Publishes Home Assistant MQTT discovery documents as retained messages.
 *
 * <p>Discovery is retried until every document was published, and then refreshed periodically so a
 * broker restart that loses retained state does not leave entities missing. Set
 * {@code maestro.home-assistant.enabled=false} to disable.
 */
@Component
public class HomeAssistantDiscovery {

    private static final Logger log = LoggerFactory.getLogger(HomeAssistantDiscovery.class);

    private final MaestroProperties properties;
    private final DiscoveryPayloadFactory factory;
    private final MqttPublisher publisher;
    private final AtomicLong lastPublishedAt = new AtomicLong();
    private final AtomicBoolean firstPublish = new AtomicBoolean(true);

    public HomeAssistantDiscovery(MaestroProperties properties, DiscoveryPayloadFactory factory,
            MqttPublisher publisher) {
        this.properties = properties;
        this.factory = factory;
        this.publisher = publisher;
    }

    @Scheduled(initialDelayString = "${maestro.home-assistant.initial-delay-ms:5000}",
            fixedDelayString = "${maestro.home-assistant.retry-interval-ms:60000}")
    public void publishAll() {
        if (!properties.homeAssistant().enabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        long refreshInterval = properties.homeAssistant().refreshIntervalMs();
        long last = lastPublishedAt.get();
        if (last != 0 && now - last < refreshInterval) {
            return;
        }
        List<DiscoveryEntity> entities = factory.build();
        String prefix = trimTrailingSlash(properties.homeAssistant().discoveryPrefix());
        int failed = 0;
        for (DiscoveryEntity entity : entities) {
            String topic = prefix + "/" + entity.component() + "/"
                    + properties.homeAssistant().deviceId() + "_" + entity.objectId() + "/config";
            if (!publisher.publishJson(topic, entity.payload(), true)) {
                failed++;
            }
        }
        if (failed == 0) {
            lastPublishedAt.set(now);
            if (firstPublish.compareAndSet(true, false)) {
                log.info("Home Assistant discovery published ({} entities on '{}')", entities.size(), prefix);
            } else {
                log.debug("Home Assistant discovery refreshed ({} entities on '{}')", entities.size(), prefix);
            }
        } else {
            log.warn("Home Assistant discovery incomplete: {} of {} entities failed, retrying",
                    failed, entities.size());
        }
    }

    public boolean isPublished() {
        return lastPublishedAt.get() != 0;
    }

    private static String trimTrailingSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }
}
