package io.droidme.maestrogateway.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ChangeDetectorTest {

    private final ChangeDetector detector = new ChangeDetector();

    @Test
    void reportsEverythingOnTheFirstPoll() {
        Map<String, Object> changed = detector.changed(Map.of(
                "Ambient_Temperature", 19.0,
                "Stove_State", 11));

        assertThat(changed).containsEntry("Ambient_Temperature", 19.0)
                .containsEntry("Stove_State", 11);
        assertThat(detector.size()).isEqualTo(2);
    }

    @Test
    void reportsOnlyChangedValuesAfterwards() {
        detector.changed(Map.of("Ambient_Temperature", 19.0, "Stove_State", 11));

        Map<String, Object> changed = detector.changed(Map.of("Ambient_Temperature", 20.0, "Stove_State", 11));

        assertThat(changed).containsOnlyKeys("Ambient_Temperature");
        assertThat(changed).containsEntry("Ambient_Temperature", 20.0);
    }

    @Test
    void clearForcesAFullRepublish() {
        detector.changed(Map.of("Ambient_Temperature", 19.0));
        detector.clear();

        Map<String, Object> changed = detector.changed(Map.of("Ambient_Temperature", 19.0));

        assertThat(changed).containsEntry("Ambient_Temperature", 19.0);
    }
}
