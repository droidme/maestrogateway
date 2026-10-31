package io.droidme.maestrogateway.protocol;

import java.util.Locale;

/** Builds information frames for tests. The first value maps to frame index 1 (Stove_State). */
final class FrameFixtures {

    private FrameFixtures() {
    }

    static String frame(int... valuesAtIndex) {
        StringBuilder frame = new StringBuilder("01");
        for (int value : valuesAtIndex) {
            frame.append('|').append(Integer.toHexString(value).toUpperCase(Locale.ROOT));
        }
        return frame.toString();
    }
}
