package ti4.service.testbed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.model.TestBedPreset;
import ti4.model.TestBedPreset.CardPick;
import ti4.model.TestBedPreset.Seat;
import ti4.testUtils.BaseTi4Test;
import tools.jackson.core.JacksonException;

class TestBedPresetServiceTest extends BaseTi4Test {

    // Every preset shipped in data/testbed must parse and pass validation, so `/testbed apply` never offers a broken
    // one.
    @Test
    void shippedPresetsParseAndValidate() throws Exception {
        List<Path> files = TestBedPresetService.shippedPresetFiles();
        assertFalse(files.isEmpty(), "expected shipped presets in data/testbed");
        for (Path file : files) {
            TestBedPreset preset = TestBedPresetService.parse(Files.readString(file));
            assertEquals(
                    List.of(),
                    TestBedPresetService.validate(preset),
                    file.getFileName().toString());
        }
        assertEquals(files.size(), TestBedPresetService.loadShippedPresets().size(), "preset names must be unique");
    }

    // The fallback map used when a preset has no mapString must only reference real tiles.
    @Test
    void defaultMapStringIsValid() {
        TestBedPreset preset = minimalPreset();
        preset.setMapString(TestBedPresetService.DEFAULT_MAP_STRING);
        assertEquals(List.of(), TestBedPresetService.validate(preset));
    }

    // Card counts accept a number, a single id, or a list mixing ids and random counts.
    @Test
    void cardPicksAcceptNumbersIdsAndMixedLists() {
        TestBedPreset preset = TestBedPresetService.parse("""
                { "you": { "acs": 2, "sos": "ans", "relics": ["shard", 1, 2] } }""");
        Seat you = preset.getYou();
        assertEquals(new CardPick(List.of(), 2), you.getAcs());
        assertEquals(new CardPick(List.of("ans"), 0), you.getSos());
        assertEquals(new CardPick(List.of("shard"), 3), you.getRelics());
    }

    // A typo in a field name must fail loudly instead of silently doing nothing.
    @Test
    void unknownFieldsAreRejected() {
        assertThrows(JacksonException.class, () -> TestBedPresetService.parse("""
                { "you": { "factoin": "sol" } }"""));
    }

    @Test
    void validationReportsEveryProblem() {
        TestBedPreset preset = TestBedPresetService.parse("""
                {
                  "you": { "faction": "notafaction", "ccs": "3-3-2", "acs": ["notacard"] },
                  "seats": [ { "faction": "sol", "sc": 9 }, { "faction": "sol", "home": "nowhere" } ],
                  "defaults": { "faction": "nekro" },
                  "start": "lunch"
                }""");
        List<String> errors = TestBedPresetService.validate(preset);
        assertContains(errors, "Unknown faction `notafaction`");
        assertContains(errors, "Faction `sol` is used twice");
        assertContains(errors, "`ccs` must look like");
        assertContains(errors, "unknown action card `notacard`");
        assertContains(errors, "Strategy card `9` is not 1-8");
        assertContains(errors, "Unknown home position `nowhere`");
        assertContains(errors, "`defaults` may not set");
        assertContains(errors, "`start` must be one of");
    }

    // Only six default home positions exist, so a seventh seat without `home` cannot be placed.
    @Test
    void seatsWithoutHomeAreLimitedToTheDefaultPositions() {
        TestBedPreset preset = TestBedPresetService.parse("""
                { "you": {}, "seats": [{}, {}, {}, {}, {}, {}] }""");
        assertContains(TestBedPresetService.validate(preset), "Too many seats without a `home`");
    }

    // A combat can only be opened once there is an action phase, and only on a real map position.
    @Test
    void combatNeedsActionStartAndValidPositions() {
        TestBedPreset preset = TestBedPresetService.parse("""
                { "you": {}, "combat": ["nowhere"], "start": "setup" }""");
        List<String> errors = TestBedPresetService.validate(preset);
        assertContains(errors, "Unknown `combat` position `nowhere`");
        assertContains(errors, "`combat` needs `start` to be `action`");
    }

    // Seat values win over defaults; defaults fill only what the seat leaves out.
    @Test
    void seatValuesOverrideDefaults() {
        TestBedPreset preset = TestBedPresetService.parse("""
                { "you": { "faction": "sol", "tg": 7 }, "defaults": { "tg": 2, "acs": 3 } }""");
        Seat merged = TestBedPresetService.withDefaults(preset.getYou(), preset.getDefaults());
        assertEquals("sol", merged.getFaction());
        assertEquals(7, merged.getTg());
        assertEquals(new CardPick(List.of(), 3), merged.getAcs());
        assertNull(merged.getSos());
    }

    private static TestBedPreset minimalPreset() {
        TestBedPreset preset = new TestBedPreset();
        preset.setYou(new Seat());
        return preset;
    }

    private static void assertContains(List<String> errors, String fragment) {
        assertTrue(errors.stream().anyMatch(error -> error.contains(fragment)), fragment + " not in " + errors);
    }
}
