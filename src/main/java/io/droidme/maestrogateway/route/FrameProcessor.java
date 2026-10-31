package io.droidme.maestrogateway.route;

import io.droidme.maestrogateway.protocol.InfoFrameParser;
import java.util.Map;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

/** Parses a raw information frame into a map. An unparsable frame becomes a {@code null} body. */
@Component
public class FrameProcessor implements Processor {

    private final InfoFrameParser parser;

    public FrameProcessor(InfoFrameParser parser) {
        this.parser = parser;
    }

    @Override
    public void process(Exchange exchange) {
        String frame = exchange.getIn().getBody(String.class);
        Map<String, Object> parsed = parser.parse(frame);
        exchange.getIn().setBody(parsed.isEmpty() ? null : parsed);
    }
}
