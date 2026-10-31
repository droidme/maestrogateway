package io.droidme.maestrogateway.route;

import io.droidme.maestrogateway.service.MqttPublisher;
import java.util.Map;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

/** Publishes changed fields to MQTT, honouring the configured payload type. */
@Component
public class InfoPublishProcessor implements Processor {

    private final MqttPublisher publisher;

    public InfoPublishProcessor(MqttPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void process(Exchange exchange) {
        Object body = exchange.getIn().getBody();
        if (body instanceof Map<?, ?> map) {
            publisher.publishInfo((Map<String, Object>) map);
        }
    }
}
