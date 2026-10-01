package ti4.service.testbed;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static ti4.service.testbed.TestBedFixture.assertContains;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import ti4.game.Game;
import ti4.model.TestBedPreset;
import ti4.model.TestBedPreset.CardPick;
import ti4.model.TestBedPreset.Seat;
import ti4.model.TestBedScript;
import ti4.model.TestBedScript.Shortcut;
import ti4.model.TestBedScript.Step;
import ti4.testUtils.BaseTi4Test;
import tools.jackson.core.JacksonException;

// Presets, scripts and everything the test bed stores in a game: parsing, validation and save-format safety.
class TestBedDataTest extends BaseTi4Test {

    static Stream<Path> shippedPresets() {
        return TestBedPresetService.shippedPresetFiles().stream();
    }

    static Stream<Path> shippedScripts() {
        return TestBedScriptService.shippedScriptFiles().stream();
    }

    // Each shipped file is its own test case, so a broken preset or script is named in the build output.
    @ParameterizedTest(name = "preset {0}")
    @MethodSource("shippedPresets")
    void shippedPresetIsValid(Path file) throws Exception {
        assertEquals(List.of(), TestBedPresetService.validate(TestBedPresetService.parse(Files.readString(file))));
    }

    @ParameterizedTest(name = "script {0}")
    @MethodSource("shippedScripts")
    void shippedScriptIsValid(Path file) throws Exception {
        assertEquals(List.of(), TestBedScriptService.validate(TestBedScriptService.parse(Files.readString(file))));
    }

    // Autocomplete and lookups go by name, so names must be unique; the scripts folder must not be read as presets.
    @Test
    void shippedFilesExistWithUniqueNames() {
        List<Path> presets = TestBedPresetService.shippedPresetFiles();
        List<Path> scripts = TestBedScriptService.shippedScriptFiles();
        assertFalse(presets.isEmpty());
        assertFalse(scripts.isEmpty());
        assertEquals(presets.size(), TestBedPresetService.loadShippedPresets().size());
        assertEquals(scripts.size(), TestBedScriptService.loadShippedScripts().size());
        assertTrue(presets.stream().noneMatch(file -> file.toString().contains("scripts")));
    }

    @Test
    void defaultMapStringIsValid() {
        TestBedPreset preset = new TestBedPreset();
        preset.setYou(new Seat());
        preset.setMapString(TestBedPresetService.DEFAULT_MAP_STRING);
        assertEquals(List.of(), TestBedPresetService.validate(preset));
    }

    @Test
    void presetValidationReportsEveryProblem() {
        TestBedPreset preset = TestBedPresetService.parse("""
                {
                  "you": { "faction": "notafaction", "ccs": "3-3-2", "acs": ["notacard"] },
                  "seats": [ { "faction": "sol", "sc": 9 }, { "faction": "sol", "home": "nowhere" } ],
                  "defaults": { "faction": "nekro" },
                  "start": "lunch",
                  "combat": ["nowhere"],
                  "shortcuts": [ { "steps": [ { "as": "jolnar", "press": "Pass" } ] } ]
                }""");
        assertContains(
                TestBedPresetService.validate(preset),
                "Unknown faction `notafaction`",
                "Faction `sol` is used twice",
                "`ccs` must look like",
                "unknown action card `notacard`",
                "Strategy card `9` is not 1-8",
                "Unknown home position `nowhere`",
                "`defaults` may not set",
                "`start` must be one of",
                "Unknown `combat` position `nowhere`",
                "`combat` needs `start` to be `action`",
                "shortcut 1: needs a `label`");
    }

    // Only six default homes exist, so a seventh seat without `home` cannot be placed.
    @Test
    void presetSeatsWithoutHomeAreLimited() {
        TestBedPreset preset = TestBedPresetService.parse("""
                { "you": {}, "seats": [{}, {}, {}, {}, {}, {}] }""");
        assertContains(TestBedPresetService.validate(preset), "Too many seats without a `home`");
    }

    @Test
    void scriptValidationReportsEveryProblem() {
        TestBedScript script = TestBedScriptService.parse("""
                {
                  "preset": "no-such-preset",
                  "steps": [
                    { "note": "two verbs", "wait": 1 },
                    { "press": "Pass" },
                    { "do": "explode" },
                    { "do": "setStored", "key": "x" },
                    { "as": "sol", "press": "Pass", "in": "sol:attic" },
                    { "expect": { "state": "sol.mood", "equals": "happy" } },
                    { "expect": { "in": "main", "contains": ["a", "b"], "count": 2 } },
                    { "expect": { "in": "main" } },
                    { "do": "hand", "as": "all", "hand": { "faction": "sol" } },
                    { "do": "setStored", "key": "a", "value": "b,c" },
                    { "as": "sol", "pressId": "%s" }
                  ],
                  "shortcuts": [ { "steps": [ { "do": "runCron" } ] } ]
                }""".formatted("x".repeat(101)));
        assertContains(
                TestBedScriptService.validate(script),
                "Unknown preset `no-such-preset`",
                "step 1: needs exactly one of",
                "step 2: needs `as`",
                "step 3: unknown action `explode`",
                "step 4: needs `value`",
                "step 5: unknown scope `sol:attic`",
                "step 6: unknown seat field `mood`",
                "step 7: `count` needs exactly one `contains` text",
                "step 8: a message check needs",
                "step 9: `hand` may not set faction",
                "step 10: `setStored` key and value may not contain",
                "step 11: `pressId` is longer than Discord's 100-character limit",
                "shortcut 1: needs a `label`",
                "shortcut 1 step 1: needs `value`");
    }

    // With a preset of fixed factions, a misspelled seat fails validation instead of failing halfway through a run.
    @Test
    void scriptSeatNamesAreCheckedAgainstThePreset() {
        TestBedScript script = TestBedScriptService.parse("""
                { "preset": "action-3p",
                  "steps": [
                    { "as": "jolnr", "press": "Pass" },
                    { "expect": { "state": "hacann.tg", "equals": "1" } },
                    { "expect": { "in": "sool:cards-info", "contains": "x" } },
                    { "as": "you", "press": "Pass" },
                    { "as": "seat2", "press": "Pass" },
                    { "do": "hand", "as": "all", "hand": { "tg": 1 } }
                  ] }""");
        List<String> errors = TestBedScriptService.validate(script);
        assertContains(errors, "unknown seat `jolnr`", "unknown seat `hacann`", "unknown seat `sool`");
        assertEquals(3, errors.size(), errors.toString());
    }

    // Placeholders are checked when the script is validated: syntax, card ids, seat names, and whether anything
    // actually gives that card to that seat before it is used.
    @Test
    void placeholdersAreValidated() {
        TestBedScript script = TestBedScriptService.parse("""
                { "preset": "ac-2p",
                  "steps": [
                    { "as": "sol", "press": "ac_play_from_hand_{ac:economic_initiative}" },
                    { "as": "sol", "press": "ac_play_from_hand_{ac:sabo2}" },
                    { "do": "hand", "as": "hacan", "hand": { "acs": ["sabo2"] } },
                    { "as": "hacan", "press": "ac_play_from_hand_{ac:sabo2}" },
                    { "as": "sol", "press": "{xx:sabo1}" },
                    { "as": "sol", "press": "{ac:notacard}" },
                    { "as": "sol", "press": "{hacan.mood}" },
                    { "as": "sol", "press": "{arborec.color}" },
                    { "expect": { "state": "sol.acIds", "notContains": "economic_initiative" } }
                  ] }""");
        List<String> errors = TestBedScriptService.validate(script);
        assertContains(
                errors,
                "step 2: `{ac:sabo2}` but nothing gives `sabo2` to sol",
                "step 5: unknown card kind in `{xx:sabo1}`",
                "step 6: unknown ac id `notacard`",
                "step 7: unknown seat attribute in `{hacan.mood}`",
                "step 8: unknown seat `arborec`");
        assertEquals(5, errors.size(), errors.toString());
    }

    // The runner resolves placeholders by writing a step to JSON and reading it back, so that must be lossless.
    @Test
    void stepsSurviveAJsonRoundTrip() {
        Step step = TestBedScriptService.parse("""
                { "steps": [ { "label": "x", "do": "hand", "as": "all", "settleSeconds": 4,
                               "hand": { "tg": 2, "acs": ["sabo1", 2], "units": { "home": "2 gf" } } } ] }""").getSteps().getFirst();
        assertEquals(step, TestBedPresetService.parse(TestBedPresetService.toJson(step), Step.class));
    }

    @Test
    void parsingAcceptsShortForms() {
        TestBedPreset preset = TestBedPresetService.parse("""
                { "you": { "faction": "sol", "tg": 7, "acs": 2, "sos": "ans", "relics": ["shard", 1, 2] },
                  "defaults": { "tg": 2, "acs": 3, "commodities": 1 } }""");
        Seat you = preset.getYou();
        assertEquals(new CardPick(List.of(), 2), you.getAcs());
        assertEquals(new CardPick(List.of("ans"), 0), you.getSos());
        assertEquals(new CardPick(List.of("shard"), 3), you.getRelics());

        Seat merged = TestBedPresetService.withDefaults(you, preset.getDefaults());
        assertEquals(7, merged.getTg());
        assertEquals(1, merged.getCommodities());
        assertEquals(new CardPick(List.of(), 2), merged.getAcs());
        assertNull(merged.getLeaders());

        TestBedScript script = TestBedScriptService.parse("""
                { "steps": [ { "do": "startPhase", "value": "action" },
                             { "expect": { "in": "main", "contains": "Politics" } } ] }""");
        assertEquals(List.of("do"), script.getSteps().getFirst().verbs());
        assertEquals(List.of("Politics"), script.getSteps().get(1).getExpect().getContains());
    }

    // A typo in a field name must fail loudly instead of silently doing nothing.
    @Test
    void unknownFieldsAreRejected() {
        assertThrows(JacksonException.class, () -> TestBedPresetService.parse("{ \"you\": { \"factoin\": \"sol\" } }"));
        assertThrows(JacksonException.class, () -> TestBedScriptService.parse("{ \"stepz\": [] }"));
    }

    // Game stored values are saved as `key,value:` and split on every ',' and ':' when loaded, so anything the
    // test bed stores must avoid them. The guard refuses unsafe values instead of silently corrupting the save.
    @Test
    void storedValuesStaySaveSafe() {
        Game game = new Game();
        assertThrows(IllegalArgumentException.class, () -> TestBedService.store(game, "key", "a,b"));
        assertThrows(IllegalArgumentException.class, () -> TestBedService.store(game, "key", "a:b"));
        assertThrows(IllegalArgumentException.class, () -> TestBedService.store(game, "bad,key", "ok"));
        assertDoesNotThrow(() -> TestBedService.store(game, "key", "fine_value-1"));

        TestBedChannelService.recordCreatedChannel(game, "111");
        TestBedChannelService.recordCreatedChannel(game, "222");
        assertEquals(List.of("111", "222"), TestBedChannelService.createdChannelIds(game));

        TestBedPreset preset = TestBedPresetService.parse("""
                { "you": {},
                  "shortcuts": [ { "label": "Hacan gets Sabotage: now, please",
                                   "steps": [ { "do": "hand", "as": "hacan", "hand": { "acs": ["sabo2", 1] } } ] } ] }""");
        TestBedShortcuts.store(game, preset.getShortcuts());
        List<Shortcut> loaded = TestBedShortcuts.load(game);
        assertEquals("Hacan gets Sabotage: now, please", loaded.getFirst().getLabel());
        assertEquals(
                new CardPick(List.of("sabo2"), 1),
                loaded.getFirst().getSteps().getFirst().getHand().getAcs());

        TestBedShortcuts.store(game, List.of());
        assertTrue(TestBedShortcuts.load(game).isEmpty());
    }
}
