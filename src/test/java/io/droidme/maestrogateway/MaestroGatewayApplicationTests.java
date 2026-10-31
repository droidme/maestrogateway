package io.droidme.maestrogateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.droidme.maestrogateway.service.ChangeDetector;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "maestro.stove.auto-connect=false",
        "maestro.mqtt.enabled=false",
        "maestro.home-assistant.enabled=false"
})
class MaestroGatewayApplicationTests {

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private ProducerTemplate producerTemplate;

    @Autowired
    private ChangeDetector changeDetector;

    @Test
    void contextLoadsWithTheExpectedRoutes() {
        assertThat(camelContext.getRoute("stove-frame")).isNotNull();
        assertThat(camelContext.getRoute("stove-poll")).isNotNull();
        assertThat(camelContext.getRoute("mqtt-command")).isNull();
    }

    @Test
    void informationFramesFlowThroughTheRouteIntoTheChangeCache() {
        changeDetector.clear();

        producerTemplate.sendBody("direct:maestroFrame", "01|0B|01|00|00|9B|2F");

        // Six frame fields plus derived Power and Diagnostics.
        assertThat(changeDetector.size()).isEqualTo(8);
    }

    @Test
    void invalidFramesAreIgnored() {
        changeDetector.clear();

        producerTemplate.sendBody("direct:maestroFrame", "AA|251220261636");

        assertThat(changeDetector.size()).isZero();
    }
}
