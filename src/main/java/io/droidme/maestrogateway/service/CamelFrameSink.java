package io.droidme.maestrogateway.service;

import org.apache.camel.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Feeds complete stove frames into the Camel route {@code direct:maestroFrame}. */
@Component
public class CamelFrameSink implements FrameSink {

    private static final Logger log = LoggerFactory.getLogger(CamelFrameSink.class);

    private final ObjectProvider<ProducerTemplate> templateProvider;

    public CamelFrameSink(ObjectProvider<ProducerTemplate> templateProvider) {
        this.templateProvider = templateProvider;
    }

    @Override
    public void accept(String frame) {
        try {
            templateProvider.getObject().sendBody("direct:maestroFrame", frame);
        } catch (Exception e) {
            log.warn("Could not process stove frame: {}", rootMessage(e));
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() != null ? current.getMessage() : current.getClass().getSimpleName();
    }
}
