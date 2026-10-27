package ti4.service.fow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import ti4.model.ColorModel;
import ti4.model.Mapper;
import ti4.service.fow.CommsColorMarker.Hue;
import ti4.testUtils.BaseTi4Test;

// Circle = main color, heart = minor tone, shown only when it differs from the main one.
class CommsColorMarkerTest extends BaseTi4Test {

    @Test
    void solidColorsUseTheirCuratedFamily() {
        assertEquals("🔴", CommsColorMarker.of("red"));
        assertEquals("🔵", CommsColorMarker.of("petrol"));
        assertEquals("🟤", CommsColorMarker.of("tan"));
        assertEquals("🌸", CommsColorMarker.of("pink"));
    }

    // The heart shows the actual shade when it drifts from the family: a bluish gray, a pinkish purple.
    @Test
    void solidColorsShowTheirShadeWhenItDiffers() {
        assertEquals("⚫💙", CommsColorMarker.of("gray"));
        assertEquals("🟣🩷", CommsColorMarker.of("plum"));
        assertEquals("🌸💜", CommsColorMarker.of("rose"));
    }

    @Test
    void splitColorsPairPrimaryWithSecondary() {
        assertEquals("🔴🖤", CommsColorMarker.of("splitred"));
        assertEquals("🔵🤍", CommsColorMarker.of("splitnavy"));
        assertEquals("🌸🖤", CommsColorMarker.of("splitpink"));
        assertEquals("⚪🖤", CommsColorMarker.of("orca"));
    }

    // Gradients ignore their category, which describes the whole blend rather than the main tone.
    @Test
    void gradientsReadBothEnds() {
        assertEquals("🔴💚", CommsColorMarker.of("watermelon"));
        assertEquals("🔴💛", CommsColorMarker.of("magma"));
        assertEquals("🔵🩷", CommsColorMarker.of("sunset"));
        assertEquals("🟠🤎", CommsColorMarker.of("copper"));
    }

    // Their stored colors are placeholders; they are drawn as rainbows.
    @Test
    void rainbowColorsGetARainbow() {
        assertEquals(CommsColorMarker.RAINBOW, CommsColorMarker.of("rainbow"));
        assertEquals(CommsColorMarker.RAINBOW, CommsColorMarker.of("sherbet"));
        assertEquals(CommsColorMarker.RAINBOW, CommsColorMarker.of("paintball"));
        assertEquals("🌈🤍", CommsColorMarker.of("splitrainbow"));
    }

    @Test
    void unknownColorFallsBackToWhite() {
        assertEquals("⚪", CommsColorMarker.of("notacolor"));
    }

    @Test
    void everyColorStartsWithACircleOrRainbow() {
        Set<String> starts = Arrays.stream(Hue.values()).map(hue -> hue.circle).collect(Collectors.toSet());
        for (ColorModel color : Mapper.getColors()) {
            String marker = CommsColorMarker.of(color.getName());
            assertTrue(
                    marker.startsWith(CommsColorMarker.RAINBOW)
                            || starts.stream().anyMatch(marker::startsWith),
                    color.getName() + " -> " + marker);
        }
    }
}
