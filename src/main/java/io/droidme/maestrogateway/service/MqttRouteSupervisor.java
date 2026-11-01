package io.droidme.maestrogateway.service;

import io.droidme.maestrogateway.config.MaestroProperties;
import io.droidme.maestrogateway.route.MaestroRoutes;
import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Starts the MQTT command subscription once the broker is reachable.
 *
 * <p>The Paho consumer connects during route start and throws when the broker is down. The route is
 * therefore declared with {@code autoStartup=false} and started from here, first when the
 * application is ready and then retried until it succeeds. This keeps the gateway usable (and the
 * stove poll alive) while the broker boots.
 */
@Component
public class MqttRouteSupervisor implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(MqttRouteSupervisor.class);

    private final CamelContext camelContext;
    private final MaestroProperties properties;

    public MqttRouteSupervisor(CamelContext camelContext, MaestroProperties properties) {
        this.camelContext = camelContext;
        this.properties = properties;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        ensureSubscribed();
    }

    @Scheduled(initialDelayString = "${maestro.mqtt.start-retry-interval-ms:10000}",
            fixedDelayString = "${maestro.mqtt.start-retry-interval-ms:10000}")
    public void ensureSubscribed() {
        if (!properties.mqtt().enabled()) {
            return;
        }
        ServiceStatus status = camelContext.getRouteController().getRouteStatus(MaestroRoutes.MQTT_COMMAND_ROUTE_ID);
        if (status == ServiceStatus.Started || status == ServiceStatus.Starting || status == ServiceStatus.Stopping) {
            return;
        }
        try {
            camelContext.getRouteController().startRoute(MaestroRoutes.MQTT_COMMAND_ROUTE_ID);
            log.info("MQTT: subscribed to {}", properties.mqtt().commandPrefix());
        } catch (Exception e) {
            log.debug("MQTT: broker {} not reachable yet ({}), retrying",
                    properties.mqtt().brokerUrl(), rootMessage(e));
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
