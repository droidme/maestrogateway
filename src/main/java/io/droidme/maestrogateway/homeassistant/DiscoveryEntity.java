package io.droidme.maestrogateway.homeassistant;

import java.util.Map;

/**
 * One Home Assistant discovery entity.
 *
 * @param component Home Assistant component ({@code sensor}, {@code binary_sensor}, {@code switch},
 *                  {@code number}, {@code button})
 * @param objectId  stable object id used in the discovery topic
 * @param payload   discovery configuration document
 */
public record DiscoveryEntity(String component, String objectId, Map<String, Object> payload) {
}
