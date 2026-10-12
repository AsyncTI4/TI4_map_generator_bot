package ti4.service.testbed;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static ti4.service.testbed.TestBedFixture.assertContains;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import ti4.game.Game;
import ti4.model.TestBedPreset;
import ti4.model.TestBedPreset.CardPick;
import ti4.model.TestBedScript;
import ti4.model.TestBedScript.Shortcut;
import ti4.model.TestBedScript.Step;
import ti4.testUtils.BaseTi4Test;
import tools.jackson.core.JacksonException;

// Presets, scripts and test button files: every file is valid, mistakes are caught, and stored data is save-safe.
class TestBedDataTest extends BaseTi4Test {

    static Stream<Path> presetFiles() {
        return TestBedPresetService.allPresetFiles().stream();
    }

    static Stream<Path> scriptFiles() {
        return TestBedScriptService.allScriptFiles().stream();
    }

    static Stream<Path> buttonFiles() {
        return Stream.concat(TestBedShortcuts.shortcutFiles().stream(), TestBedShortcuts.localShortcutFiles().stream());
    }

    // Each file (shipped, and your own in data/testbed/local) is its own case, so a broken one is named.
    @ParameterizedTest(name = "preset {0}")
    @MethodSource("presetFiles")
    void presetIsValid(Path file) throws Exception {
        assertEquals(List.of(), TestBedPresetService.validate(TestBedPresetService.parse(Files.readString(file))));
    }

    @ParameterizedTest(name = "script {0}")
    @MethodSource("scriptFiles")
    void scriptIsValid(Path file) throws Exception {
        assertEquals(List.of(), TestBedScriptService.validate(TestBedScriptService.parse(Files.readString(file))));
    }

    @ParameterizedTest(name = "test buttons {0}")
    @MethodSource("buttonFiles")
    void buttonFileIsValid(Path file) throws Exception {
        assertEquals(List.of(), TestBedShortcuts.validateGroup(TestBedShortcuts.parseGroup(Files.readString(file))));
    }

    // Names and ids are used for lookups and button ids, so they must be unique; the shared base file must ship.
    @Test
    void shippedFilesAreConsistent() {
        List<Path> presets = TestBedPresetService.shippedPresetFiles();
        List<Path> scripts = TestBedScriptService.shippedScriptFiles();
        assertFalse(presets.isEmpty());
        assertEquals(presets.size(), TestBedPresetService.readPresets(presets).size());
        assertEquals(scripts.size(), TestBedScriptService.readScripts(scripts).size());
        assertTrue(TestBedShortcuts.shortcutFiles().stream()
                .anyMatch(file -> "shared".equals(TestBedShortcuts.fileName(file))));
        List<String> javaIds = TestBedShortcuts.javaShortcuts().stream()
                .map(shortcut -> TestBedShortcuts.keyOf(shortcut.group()) + "/" + shortcut.id())
                .toList();
        assertEquals(javaIds.size(), javaIds.stream().distinct().count(), javaIds.toString());
    }

    // Mistakes fail validation with a clear message instead of halfway through a live run.
    @Test
    void validationCatchesMistakes() {
        TestBedPreset preset = TestBedPresetService.parse("""
                {
                  "you": { "faction": "notafaction", "ccs": "3-3-2", "acs": ["notacard"], "breakthrough": "sleepy",
                           "pns": ["notanote"], "scoredObjectives": ["develop"] },
                  "seats": [ { "faction": "sol", "sc": 9 }, { "faction": "sol", "home": "nowhere" } ],
                  "start": "lunch",
                  "combat": ["101"],
                  "tokens": { "nowhere": ["notatoken"] }
                }""");
        assertContains(
                TestBedPresetService.validate(preset),
                "Unknown faction `notafaction`",
                "Faction `sol` is used twice",
                "`ccs` must look like",
                "unknown action card `notacard`",
                "`breakthrough` must be one of",
                "unknown promissory note `notanote`",
                "Objective `develop` is scored but not in `revealedObjectives`",
                "Strategy card `9` is not 1-8",
                "Unknown home position `nowhere`",
                "`start` must be one of",
                "`combat` needs `start` to be `action`",
                "`tokens` target `nowhere`",
                "unknown token `notatoken`");

        List<String> scriptErrors =
                TestBedScriptService.validate(TestBedScriptService.parse("""
                { "preset": "ac-2p",
                  "steps": [
                    { "note": "two verbs", "wait": 1 },
                    { "do": "explode" },
                    { "as": "hacn", "press": "Pass" },
                    { "expect": { "state": "sol.mood", "equals": "happy" } },
                    { "as": "sol", "press": "ac_play_from_hand_{ac:sabo2}" },
                    { "do": "setStored", "key": "a", "value": "b,c" },
                    { "as": "sol", "pressId": "%s" }
                  ] }""".formatted("x".repeat(101))));
        assertContains(
                scriptErrors,
                "step 1: needs exactly one of",
                "step 2: unknown action `explode`",
                "step 3: unknown seat `hacn`",
                "step 4: unknown seat field `mood`",
                "step 5: `{ac:sabo2}` but nothing gives `sabo2` to sol",
                "step 6: `setStored` key and value may not contain",
                "step 7: `pressId` is longer than Discord's 100-character limit");

        // Fields that a kind of check would silently ignore, typos in scopes, and checks with nothing to check.
        List<String> expectErrors = TestBedScriptService.validate(TestBedScriptService.parse("""
                { "steps": [
                    { "expect": { "ephemeral": "for someone else" } },
                    { "as": "you", "press": "Pass" },
                    { "expect": { "in": "main", "contains": "x", "equals": "y" } },
                    { "expect": { "ephemeral": "x", "contains": "y" } },
                    { "expect": { "state": "game.round", "equals": "2", "noFactionLeak": true } },
                    { "expect": { "in": "mian", "contains": "x" } },
                    { "expect": { "in": "main", "contains": "x", "since": "yesterday" } },
                    { "expect": { "in": "main", "matches": "(" } },
                    { "expect": { "state": "tile.101.mood", "equals": "x" } }
                  ] }"""));
        assertContains(
                expectErrors,
                "step 1: an `ephemeral` or `modal` check needs a `press` before it",
                "step 3: `equals` only works with `state`",
                "step 4: an `ephemeral` or `modal` check ignores [contains]",
                "step 5: a `state` check ignores [noFactionLeak]",
                "step 6: scope `mian` is neither main, actions, gm nor a seat",
                "step 7: `since` must be",
                "step 8: `matches` is not a valid regex",
                "step 9: unknown tile. field `mood`");
    }

    // Script authors (often agents) learn the vocabulary from DEVELOPER_TESTBED.md; anything the code accepts but the
    // reference does not mention is invisible to them.
    @Test
    void referenceDocumentsEveryVerbActionScopeAndStatePath() throws Exception {
        String reference = Files.readString(Path.of("DEVELOPER_TESTBED.md"));
        List<String> names = new ArrayList<>();
        names.addAll(TestBedScript.VERBS);
        names.addAll(TestBedScript.ACTIONS);
        names.addAll(TestBedScriptService.SHARED_SCOPES);
        names.addAll(TestBedStateResolver.SEAT_FIELDS);
        names.addAll(TestBedStateResolver.GAME_FIELDS);
        names.addAll(TestBedStateResolver.TILE_FIELDS);
        names.addAll(TestBedStateResolver.PLANET_FIELDS);
        List<String> missing = names.stream()
                .filter(name -> !reference.contains("`" + name + "`")
                        && !reference.contains("`" + name + " ")
                        && !reference.contains("`" + name + "(")
                        && !reference.contains(" " + name + "`"))
                .toList();
        List<String> missingScopes = TestBedScriptService.SEAT_SCOPES.stream()
                .filter(scope -> !reference.contains("`<seat>:" + scope + "`"))
                .toList();
        assertEquals(List.of(), missing, "add these to DEVELOPER_TESTBED.md");
        assertEquals(List.of(), missingScopes, "add these scopes to DEVELOPER_TESTBED.md");
    }

    // Map and fog preset fields fail validation up front: bad positions, tiles, lane matrices and fog options, and
    // maps A-G (fog-only) in a preset that is not a fog preset.
    @Test
    void mapAndFogFieldsAreValidated() {
        TestBedPreset preset = TestBedPresetService.parse("""
                {
                  "fog": false,
                  "you": { "faction": "sol" },
                  "tiles": { "a000": "39", "h101": "19", "b101": "notatile" },
                  "customHyperlanes": { "nowhere": "0", "205": "1,2,3" },
                  "fowOptions": ["map_connections", "make_it_dark"],
                  "stored": { "fowMapSegments": "" }
                }""");
        assertContains(
                TestBedPresetService.validate(preset),
                "`tiles` position `h101` is not a tile position",
                "unknown tile `notatile` at `b101`",
                "`customHyperlanes` position `nowhere` is not a tile position",
                "`customHyperlanes` at `205` needs a 6x6 matrix",
                "unknown fog option `make_it_dark`",
                "`fowOptions` need a fog game",
                "`stored` keys and values may not be blank",
                "maps A-G (`a000`-`g848`) need `\"fog\": true`");
    }

    // Border entries are `<direction>:<type>`; legacy type spellings are accepted, the retired `arrow` is not.
    @Test
    void borderAnomaliesAreValidated() {
        TestBedPreset preset = TestBedPresetService.parse("""
                {
                  "you": { "faction": "sol" },
                  "borderAnomalies": {
                    "202": ["n:spatial_tear", "s:SPATIAL_TEAR", "ne:Gravity Wave"],
                    "nowhere": ["n:nebula"],
                    "205": ["up:nebula", "n:arrow", "nebula"]
                  }
                }""");
        List<String> errors = TestBedPresetService.validate(preset);
        assertContains(
                errors,
                "`borderAnomalies` position `nowhere` is not a tile position",
                "`borderAnomalies` entry `up:nebula` at `205`",
                "`borderAnomalies` entry `n:arrow` at `205`",
                "`borderAnomalies` entry `nebula` at `205`");
        assertTrue(errors.stream().noneMatch(error -> error.contains("at `202`")), String.join("\n", errors));
    }

    // Short forms parse, typos in field names are rejected, steps survive the JSON round trip the runner uses, and
    // anything stored in a game avoids the `,` and `:` the save format splits on.
    @Test
    void parsingAndStorageAreSafe() {
        TestBedPreset preset = TestBedPresetService.parse("""
                { "you": { "acs": 2, "sos": "ans", "relics": ["shard", 1, 2] },
                  "shortcuts": [ { "label": "Hacan: Sabotage, now", "steps": [ { "do": "hand", "as": "hacan",
                                   "hand": { "acs": ["sabo2", 1], "units": { "home": "2 gf" } } } ] } ] }""");
        assertEquals(new CardPick(List.of(), 2), preset.getYou().getAcs());
        assertEquals(new CardPick(List.of("ans"), 0), preset.getYou().getSos());
        assertEquals(new CardPick(List.of("shard"), 3), preset.getYou().getRelics());
        assertThrows(JacksonException.class, () -> TestBedPresetService.parse("{ \"you\": { \"factoin\": \"sol\" } }"));

        Step step = preset.getShortcuts().getFirst().getSteps().getFirst();
        assertEquals(step, TestBedPresetService.parse(TestBedPresetService.toJson(step), Step.class));

        Game game = new Game();
        assertThrows(IllegalArgumentException.class, () -> TestBedService.store(game, "key", "a,b"));
        assertThrows(IllegalArgumentException.class, () -> TestBedService.store(game, "key", "a:b"));
        assertDoesNotThrow(() -> TestBedService.store(game, "key", "fine_value-1"));
        TestBedChannelService.recordCreatedChannel(game, "111");
        TestBedChannelService.recordCreatedChannel(game, "222");
        assertEquals(List.of("111", "222"), TestBedChannelService.createdChannelIds(game));
        TestBedShortcuts.store(game, preset.getShortcuts());
        List<Shortcut> loaded = TestBedShortcuts.load(game);
        assertEquals("Hacan: Sabotage, now", loaded.getFirst().getLabel());
    }
}
