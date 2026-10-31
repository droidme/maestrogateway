package io.droidme.maestrogateway.route;

import io.droidme.maestrogateway.service.ChangeDetector;
import java.util.Map;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

/** Keeps only the fields whose value changed since the previous poll. */
@Component
public class ChangeFilterProcessor implements Processor {

    private final ChangeDetector changeDetector;

    public ChangeFilterProcessor(ChangeDetector changeDetector) {
        this.changeDetector = changeDetector;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void process(Exchange exchange) {
        Object body = exchange.getIn().getBody();
        if (!(body instanceof Map<?, ?> map)) {
            exchange.getIn().setBody(null);
            return;
        }
        Map<String, Object> changed = changeDetector.changed((Map<String, Object>) map);
        exchange.getIn().setBody(changed.isEmpty() ? null : changed);
    }
}
