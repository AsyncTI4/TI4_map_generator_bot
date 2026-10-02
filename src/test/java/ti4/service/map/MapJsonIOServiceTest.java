package ti4.service.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.Map;
import net.dv8tion.jda.api.JDA;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.discord.JdaService;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.service.fow.LoreService;
import ti4.service.fow.LoreService.LoreEntry;
import ti4.testUtils.BaseTi4Test;

/**
 * Proves the JSON map importer's lore path is wired all the way through the real LoreService
 * pipeline (validation, {@code #tag} auto-tagging, round windows, phase lore) rather than the
 * previous silent {@code addLoreFromString} bypass, and that the bot's own JSON exporter is
 * symmetric with everything importable — an export-then-reimport round-trips.
 */
class MapJsonIOServiceTest extends BaseTi4Test {

    private static final String GAME_NAME = "test-game";
    private static final String REIMPORT_GAME_NAME = "test-game-reimport";

    private Game game;
    private Player player;

    @BeforeEach
    void setUp() {
        JdaService.testingMode = true;
        JdaService.jda = mock(JDA.class);

        // LORECACHE is keyed by game name; both names used across this test class must be evicted
        // per-test or entries leak between tests (and between the two Game objects in the
        // round-trip test, which intentionally share tiles but must not share lore storage).
        LoreService.evictGameLore(GAME_NAME);
        LoreService.evictGameLore(REIMPORT_GAME_NAME);

        game = new Game();
        game.setName(GAME_NAME);
        game.setTile(new Tile("18", "000")); // Mecatol Rex, position "000"

        player = game.addPlayer("test-user-id", "winnu");
        player.setFaction("winnu");
        player.setColor("red");
        player.setTg(5);
    }

    @Test
    void backCompatSingularFieldImports() {
        String json = """
                {
                  "mapInfo": [
                    {
                      "position": "000",
                      "tileID": "18",
                      "systemLore": {
                        "loreText": "An old beacon flickers.",
                        "footerText": "",
                        "receiver": "CURRENT",
                        "trigger": "CONTROLLED",
                        "ping": "NO",
                        "persistance": "ONCE"
                      }
                    }
                  ]
                }
                """;

        MapJsonIOService.importMapFromJson(game, json, null);

        LoreEntry stored = LoreService.getGameLore(game).get("000");
        assertNotNull(stored);
        assertEquals("An old beacon flickers.", stored.loreText);
        assertEquals(LoreService.TRIGGER.CONTROLLED, stored.trigger);
    }

    @Test
    void multiEntryViaSystemLoreEntriesAutoTagsTheCollision() {
        String json = """
                {
                  "mapInfo": [
                    {
                      "position": "000",
                      "tileID": "18",
                      "systemLoreEntries": [
                        {"loreText":"First entry","footerText":"","receiver":"CURRENT","trigger":"CONTROLLED","ping":"NO","persistance":"ONCE"},
                        {"loreText":"Second entry","footerText":"","receiver":"CURRENT","trigger":"MOVED","ping":"NO","persistance":"ONCE"}
                      ]
                    }
                  ]
                }
                """;

        MapJsonIOService.importMapFromJson(game, json, null);

        Map<String, LoreEntry> lore = LoreService.getGameLore(game);
        assertEquals(2, lore.size(), "both entries must survive, not just the first");
        assertTrue(lore.containsKey("000"), "first entry keeps the bare key");
        assertEquals("First entry", lore.get("000").loreText);

        String taggedKey = lore.keySet().stream()
                .filter(k -> k.startsWith("000#"))
                .findFirst()
                .orElse(null);
        assertNotNull(taggedKey, "second entry must be auto-tagged instead of overwriting the first");
        assertEquals("Second entry", lore.get(taggedKey).loreText);
    }

    @Test
    void roundWindowRoundTripsThroughPlanetLoreEntries() {
        String json = """
                {
                  "mapInfo": [
                    {
                      "position": "000",
                      "tileID": "18",
                      "planets": [
                        {
                          "planetID": "mr",
                          "planetLoreEntries": [
                            {"loreText":"Guarded ruins","footerText":"","receiver":"CURRENT","trigger":"CONTROLLED","ping":"NO","persistance":"ALWAYS","fromRound":3,"tillRound":6}
                          ]
                        }
                      ]
                    }
                  ]
                }
                """;

        MapJsonIOService.importMapFromJson(game, json, null);

        LoreEntry stored = LoreService.getGameLore(game).get("mr");
        assertNotNull(stored);
        assertEquals(3, stored.fromRound);
        assertEquals(6, stored.tillRound);
    }

    @Test
    void phaseLoreImportsAndFiresThroughTheRealTriggerPath() {
        game.setLoreMode(true); // non-FoW test game needs this for showPhaseStartLore to fire at all

        String json = """
                {
                  "mapInfo": [ {"position": "000", "tileID": "18"} ],
                  "phaseLore": {
                    "strategy": [
                      {"loreText":"A cold wind blows.","footerText":"!tg +1","receiver":"ALL","trigger":"PHASE_START","ping":"NO","persistance":"ALWAYS"}
                    ]
                  }
                }
                """;

        MapJsonIOService.importMapFromJson(game, json, null);
        assertTrue(LoreService.getGameLore(game).containsKey("strategy"), "phaseLore must actually be read");

        LoreService.showPhaseStartLore(game, "strategy");

        assertEquals(6, player.getTg(), "the imported entry's !tg +1 must actually fire, not just sit stored");
    }

    @Test
    void mismatchedPhaseTriggerPairingIsRejectedNotSilentlyStored() {
        String json = """
                {
                  "mapInfo": [ {"position": "000", "tileID": "18"} ],
                  "phaseLore": {
                    "strategy": [
                      {"loreText":"Bad pairing","footerText":"","receiver":"ALL","trigger":"CONTROLLED","ping":"NO","persistance":"ONCE"}
                    ]
                  }
                }
                """;

        MapJsonIOService.importMapFromJson(game, json, null);

        assertFalse(
                LoreService.getGameLore(game).containsKey("strategy"),
                "a phase target paired with a non-phase trigger must be rejected by validateLore, not silently accepted");
    }

    @Test
    void oneBadTileIsSkippedInsteadOfAbortingTheRestOfTheImport() {
        // A null position throws inside handleTile itself (PositionMapper/Properties reject a null
        // key) before any lore code runs — listed first, on purpose, so a pre-fix importer would
        // have aborted before ever reaching the second (valid) tile or the phaseLore below it.
        String json = """
                {
                  "mapInfo": [
                    {"position": null, "tileID": "18"},
                    {
                      "position": "101",
                      "tileID": "19",
                      "systemLore": {"loreText":"Still here","footerText":"","receiver":"CURRENT","trigger":"CONTROLLED","ping":"NO","persistance":"ONCE"}
                    }
                  ],
                  "phaseLore": {
                    "action": [
                      {"loreText":"Also still here","footerText":"","receiver":"ALL","trigger":"PHASE_START","ping":"NO","persistance":"ONCE"}
                    ]
                  }
                }
                """;

        MapJsonIOService.importMapFromJson(game, json, null);

        assertNotNull(game.getTileByPosition("101"), "the second, valid tile must still be added");
        assertEquals("Still here", LoreService.getGameLore(game).get("101").loreText, "its lore must still import");
        assertTrue(
                LoreService.getGameLore(game).containsKey("action"),
                "phase lore (handled after the tile loop) must still run despite the earlier tile's failure");
    }

    @Test
    void planetLoreWithAMissingPlanetIdIsSkippedNotThrown() {
        String json = """
                {
                  "mapInfo": [
                    {
                      "position": "000",
                      "tileID": "18",
                      "planets": [
                        {
                          "planetID": null,
                          "planetLoreEntries": [
                            {"loreText":"Orphaned entry","footerText":"","receiver":"CURRENT","trigger":"CONTROLLED","ping":"NO","persistance":"ONCE"}
                          ]
                        },
                        {
                          "planetID": "mr",
                          "planetLoreEntries": [
                            {"loreText":"Real entry","footerText":"","receiver":"CURRENT","trigger":"CONTROLLED","ping":"NO","persistance":"ONCE"}
                          ]
                        }
                      ]
                    }
                  ]
                }
                """;

        MapJsonIOService.importMapFromJson(game, json, null);

        assertEquals(1, LoreService.getGameLore(game).size(), "only the entry with a real planetID must land");
        assertEquals("Real entry", LoreService.getGameLore(game).get("mr").loreText);
    }

    @Test
    void exportThenReimportRoundTripsTaggedAndPhaseLore() {
        LoreEntry bare = new LoreEntry("Bare system lore");
        bare.target = "000";
        bare.receiver = LoreService.RECEIVER.CURRENT;
        bare.trigger = LoreService.TRIGGER.CONTROLLED;
        bare.fromRound = 2;
        bare.tillRound = 5;
        LoreService.getGameLore(game).put(bare.target, bare);

        LoreEntry tagged = new LoreEntry("Tagged sibling lore");
        tagged.target = "000#Extra";
        tagged.receiver = LoreService.RECEIVER.CURRENT;
        tagged.trigger = LoreService.TRIGGER.MOVED;
        LoreService.getGameLore(game).put(tagged.target, tagged);

        LoreEntry phase = new LoreEntry("Phase lore for export");
        phase.target = "action";
        phase.receiver = LoreService.RECEIVER.ALL;
        phase.trigger = LoreService.TRIGGER.PHASE_END;
        LoreService.getGameLore(game).put(phase.target, phase);

        String json = MapJsonIOService.exportMapAsJson(null, game, false, false, true);
        assertNotNull(json);

        Game freshGame = new Game();
        freshGame.setName(REIMPORT_GAME_NAME);
        freshGame.setTile(new Tile("18", "000"));

        MapJsonIOService.importMapFromJson(freshGame, json, null);

        Map<String, LoreEntry> reimported = LoreService.getGameLore(freshGame);
        assertEquals(3, reimported.size(), "bare + tagged system entries + phase entry must all round-trip");

        assertTrue(reimported.containsKey("000"));
        assertEquals(2, reimported.get("000").fromRound, "round window must survive export+reimport");
        assertEquals(5, reimported.get("000").tillRound);

        long siblingsAtBase001 = reimported.keySet().stream()
                .filter(k -> "000".equals(k) || k.startsWith("000#"))
                .count();
        assertEquals(2, siblingsAtBase001, "both the bare and tagged entries at 001 must survive, re-tagged or not");

        assertTrue(reimported.containsKey("action"), "phase lore must round-trip");
        assertEquals(LoreService.TRIGGER.PHASE_END, reimported.get("action").trigger);
    }
}
