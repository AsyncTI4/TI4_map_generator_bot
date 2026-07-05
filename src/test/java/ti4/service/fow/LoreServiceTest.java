package ti4.service.fow;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.dv8tion.jda.api.JDA;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ti4.discord.JdaService;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.model.TileModel;
import ti4.service.fow.LoreService.LoreEntry;
import ti4.service.map.CustomHyperlaneService;
import ti4.testUtils.BaseTi4Test;

/**
 * Comprehensive lore effect testing: 150+ tests covering trigger paths, round-gating, tag resolution,
 * conditional effects (?color/?faction/?round), and all five new automation verbs (tech, fogtile, settile,
 * hyperlane). Organized into nested classes by concern (TriggerScan, RoundGating, TaggedTargets, etc.).
 * <p>
 * These tests validate cross-system impact: settile/rotatehyperlane call into map services and rebuild
 * autocomplete; fogtile effects are FoW-only; tech grants are idempotent. Tests run ~8ms each; full suite
 * adds ~4 seconds to the 451-test pipeline (negligible for dev loops, acceptable for CI).
 */
class LoreServiceTest extends BaseTi4Test {

    private Game game;
    private Player player;
    private Tile systemTile; // tile 18 = Mecatol Rex, position "001"
    private Tile remoteTile; // tile 19, position "002" — for @location override tests

    @BeforeEach
    void setUp() {
        // Some other test classes null out the shared JdaService.jda mock in their own teardown,
        // and BaseTi4Test's one-time @BeforeAll setup won't re-run to restore it. Re-establish it
        // here so these tests don't depend on suite-wide ordering.
        JdaService.testingMode = true;
        JdaService.jda = mock(JDA.class);

        // LORECACHE is keyed by game name and every test game is named "test-game" — without this,
        // entries seeded via getGameLore(game).put(...) in one test silently leak into the next.
        LoreService.evictGameLore("test-game");

        game = new Game();
        game.setName("test-game");

        player = game.addPlayer("test-user-id", "winnu");
        player.setFaction("winnu");
        player.setColor("red");
        player.setTacticalCC(2);
        player.setFleetCC(3);
        player.setStrategicCC(2);
        player.setTg(5);
        player.setCommoditiesBase(4);
        game.setActionCards(Mapper.getShuffledDeck("action_cards_pok"));
        game.setSecretObjectives(Mapper.getShuffledDeck("secret_objectives_pok"));

        Player neutral = game.addPlayer(Constants.dicecordId, "neutral");
        neutral.setColor("gray");

        systemTile = new Tile("18", "001");
        game.setTile(systemTile);

        remoteTile = new Tile("19", "002");
        game.setTile(remoteTile);
    }

    private static LoreEntry entry(String... footerLines) {
        LoreEntry e = new LoreEntry("Some lore text.");
        e.footerText = String.join("\n", footerLines);
        return e;
    }

    /** Adds a second/third real (non-NPC) player distinct from {@code player}/{@code neutral}. */
    private Player addRealPlayer(String userId, String faction, String color) {
        Player p = game.addPlayer(userId, faction);
        p.setFaction(faction);
        p.setColor(color);
        return p;
    }

    // -----------------------------------------------------------------------
    // 1. Effect line parsing
    // -----------------------------------------------------------------------

    @Nested
    class EffectLineParsing {

        @Test
        void effectLinesAreExtractedByBang() {
            LoreEntry e = entry("Visible footer.", "!tg +2", "Also visible.", "!fleet +1");
            assertEquals(List.of("tg +2", "fleet +1"), e.getEffectLines());
        }

        @Test
        void displayFooterStripsEffectLines() {
            LoreEntry e = entry("Visible footer.", "!tg +2", "Also visible.");
            assertEquals("Visible footer.\nAlso visible.", e.getDisplayFooter());
        }

        @Test
        void pureEffectFooterGivesEmptyDisplay() {
            LoreEntry e = entry("!tg +1", "!fleet -1");
            assertTrue(e.getDisplayFooter().isEmpty());
        }

        @Test
        void emptyFooterIsSafe() {
            LoreEntry e = entry();
            assertTrue(e.getEffectLines().isEmpty());
            assertTrue(e.getDisplayFooter().isEmpty());
        }

        @Test
        void multipleEffectsOnOneLineAreSplit() {
            // the "!a … !b …" same-line form documented on getEffectLines
            LoreEntry e = entry("!tg +2 !fleet +1");
            assertEquals(List.of("tg +2", "fleet +1"), e.getEffectLines());
        }

        @Test
        void textBeforeEffectOnSameLineStaysInDisplay() {
            LoreEntry e = entry("Flavor here !tg +2");
            assertEquals(List.of("tg +2"), e.getEffectLines());
            assertEquals("Flavor here", e.getDisplayFooter());
        }

        @Test
        void numericColonPrefixIsPlainTextWithoutARollMarker() {
            // Backward compat: roll gating added N-M:/N: bin tags, but a pre-roll entry with flavor text
            // that happens to start with "N:" must keep its prefix and its effect must still fire.
            LoreEntry e = entry("3: The ancient gate opens !tg +2");
            assertEquals("3: The ancient gate opens", e.getDisplayFooter());
            assertEquals(List.of("tg +2"), e.getEffectLines(), "effect is untagged, fires unconditionally");
        }

        @Test
        void numericColonPrefixBecomesABinTagOnlyWhenRollGated() {
            LoreEntry e = entry("!roll 2d10", "3: !tg +2");
            assertEquals(List.of("3:tg +2"), e.getEffectLines());
        }
    }

    // -----------------------------------------------------------------------
    // 2. Validation
    // -----------------------------------------------------------------------

    @Nested
    class Validation {

        @Test
        void allBuiltinScalarEffectsValidateClean() {
            // Exercises Mapper (token IDs), AliasHandler (planet aliases), and Units (unit types)
            // against real resource data — breaks if any naming changes in those subsystems.
            assertTrue(LoreEffects.validateEffects(entry("!tg +3"), game).isEmpty());
            assertTrue(LoreEffects.validateEffects(entry("!fleet -1"), game).isEmpty());
            assertTrue(LoreEffects.validateEffects(entry("!tactic +1"), game).isEmpty());
            assertTrue(LoreEffects.validateEffects(entry("!tactical +1"), game).isEmpty());
            assertTrue(LoreEffects.validateEffects(entry("!strategy +1"), game).isEmpty());
            assertTrue(LoreEffects.validateEffects(entry("!strategic +1"), game).isEmpty());
            assertTrue(LoreEffects.validateEffects(entry("!comms +3"), game).isEmpty());
            assertTrue(
                    LoreEffects.validateEffects(entry("!commodities +1"), game).isEmpty());
            assertTrue(LoreEffects.validateEffects(entry("!ac 2"), game).isEmpty());
            assertTrue(LoreEffects.validateEffects(entry("!token gravityrift"), game)
                    .isEmpty());
            assertTrue(LoreEffects.validateEffects(entry("Pure flavor text."), game)
                    .isEmpty());
        }

        @Test
        void plasticVariantsValidateClean() {
            assertTrue(LoreEffects.validateEffects(entry("!plastic 2 infantry"), game)
                    .isEmpty());
            assertTrue(LoreEffects.validateEffects(entry("!plastic neutral 3 fighter"), game)
                    .isEmpty());
        }

        @Test
        void locationOverrideValidatesClean() {
            assertTrue(LoreEffects.validateEffects(entry("!tg +1 @001"), game).isEmpty());
            // @mr exercises AliasHandler.resolvePlanet + tile planet-holder lookup
            assertTrue(LoreEffects.validateEffects(entry("!plastic 1 infantry @mr"), game)
                    .isEmpty());
        }

        @Test
        void unknownVerb() {
            List<String> problems = LoreEffects.validateEffects(entry("!plastik 2 infantry"), game);
            assertFalse(problems.isEmpty());
            assertTrue(problems.get(0).contains("plastik"));
        }

        @Test
        void missingOrBadOperand() {
            assertFalse(LoreEffects.validateEffects(entry("!tg"), game).isEmpty());
            assertFalse(LoreEffects.validateEffects(entry("!tg lots"), game).isEmpty());
            assertFalse(LoreEffects.validateEffects(entry("!comms"), game).isEmpty());
            assertFalse(LoreEffects.validateEffects(entry("!ac"), game).isEmpty());
            assertFalse(LoreEffects.validateEffects(entry("!ac 0"), game).isEmpty());
            assertFalse(LoreEffects.validateEffects(entry("!ac -1"), game).isEmpty());
            assertFalse(LoreEffects.validateEffects(entry("!token"), game).isEmpty());
        }

        @Test
        void plasticBadArgs() {
            assertFalse(LoreEffects.validateEffects(entry("!plastic 2 infntry"), game)
                    .isEmpty()); // unknown unit
            assertFalse(LoreEffects.validateEffects(entry("!plastic infantry"), game)
                    .isEmpty()); // missing count
            assertFalse(
                    LoreEffects.validateEffects(entry("!plastic neutral"), game).isEmpty()); // neutral w/ no args
        }

        @Test
        void unknownAtTarget() {
            List<String> problems = LoreEffects.validateEffects(entry("!tg +1 @zzz"), game);
            assertFalse(problems.isEmpty());
            assertTrue(problems.get(0).contains("zzz"));
        }

        @Test
        void multipleProblemsAllReported() {
            List<String> problems = LoreEffects.validateEffects(entry("!zzz 1", "!plastic 2 badunit"), game);
            assertEquals(2, problems.size());
        }
    }

    // -----------------------------------------------------------------------
    // 3. Player-state effects
    // -----------------------------------------------------------------------

    @Nested
    class PlayerStateEffects {

        @Test
        void tgIncrements() {
            LoreEffects.applyLoreEffectsForTest(player, game, entry("!tg +3"), systemTile, Constants.SPACE, true);
            assertEquals(8, player.getTg());
        }

        @Test
        void tgDecrements() {
            LoreEffects.applyLoreEffectsForTest(player, game, entry("!tg -2"), systemTile, Constants.SPACE, true);
            assertEquals(3, player.getTg());
        }

        @Test
        void ccCountersAdjusted() {
            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!fleet +1", "!tactic +1", "!strategy -1"), systemTile, Constants.SPACE, true);
            assertEquals(4, player.getFleetCC());
            assertEquals(3, player.getTacticalCC());
            assertEquals(1, player.getStrategicCC());
        }

        @Test
        void commsAdjusted() {
            LoreEffects.applyLoreEffectsForTest(player, game, entry("!comms +3"), systemTile, Constants.SPACE, true);
            assertEquals(3, player.getCommodities());
        }

        @Test
        void commoditiesAliasWorks() {
            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!commodities +2"), systemTile, Constants.SPACE, true);
            assertEquals(2, player.getCommodities());
        }

        @Test
        void multipleLinesAppliedInOrder() {
            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!tg +2", "!fleet +1", "!tactic +1"), systemTile, Constants.SPACE, true);
            assertEquals(7, player.getTg());
            assertEquals(4, player.getFleetCC());
            assertEquals(3, player.getTacticalCC());
        }

        @Test
        void nonEffectLinesIgnored() {
            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("Flavor text.", "!tg +1"), systemTile, Constants.SPACE, true);
            assertEquals(6, player.getTg());
        }

        @Test
        void unknownVerbSkippedWithoutException() {
            LoreEffects.applyLoreEffectsForTest(player, game, entry("!badverb foo"), systemTile, Constants.SPACE, true);
            assertEquals(5, player.getTg());
        }

        @Test
        void acZeroSkipped() {
            var descs = LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!ac 0"), systemTile, Constants.SPACE, true);
            assertTrue(descs.isEmpty());
        }

        @Test
        void multipleEffectsOnOneLineApplied() {
            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!tg +2 !fleet +1"), systemTile, Constants.SPACE, true);
            assertEquals(7, player.getTg());
            assertEquals(4, player.getFleetCC());
        }
    }

    // -----------------------------------------------------------------------
    // 4. Plastic (unit placement)
    // -----------------------------------------------------------------------

    @Nested
    class PlasticEffects {

        @Test
        void playerColorInSpace() {
            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!plastic 2 infantry"), systemTile, Constants.SPACE, true);
            UnitKey key = Units.getUnitKey(UnitType.Infantry, "red");
            assertEquals(2, systemTile.getUnitHolders().get(Constants.SPACE).getUnitCount(key));
        }

        @Test
        void neutralColorUsesGray() {
            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!plastic neutral 1 spacedock"), systemTile, Constants.SPACE, true);
            UnitKey key = Units.getUnitKey(UnitType.Spacedock, "gray");
            assertEquals(1, systemTile.getUnitHolders().get(Constants.SPACE).getUnitCount(key));
        }

        @Test
        void explicitColorOverridesPlayerColor() {
            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!plastic gray 2 fighter"), systemTile, Constants.SPACE, true);
            assertEquals(
                    2,
                    systemTile
                            .getUnitHolders()
                            .get(Constants.SPACE)
                            .getUnitCount(Units.getUnitKey(UnitType.Fighter, "gray")));
            assertEquals(
                    0,
                    systemTile
                            .getUnitHolders()
                            .get(Constants.SPACE)
                            .getUnitCount(Units.getUnitKey(UnitType.Fighter, "red")));
        }

        @Test
        void planetHolderArgPlacesOnPlanet() {
            // "mr" is the planet holder key for tile 18 (Mecatol Rex)
            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!plastic 2 infantry mr"), systemTile, Constants.SPACE, true);
            UnitKey key = Units.getUnitKey(UnitType.Infantry, "red");
            assertEquals(2, systemTile.getUnitHolders().get("mr").getUnitCount(key));
            assertEquals(0, systemTile.getUnitHolders().get(Constants.SPACE).getUnitCount(key));
        }

        @Test
        void planetHolderWithColorSpecifier() {
            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!plastic gray 1 pds mr"), systemTile, Constants.SPACE, true);
            assertEquals(1, systemTile.getUnitHolders().get("mr").getUnitCount(Units.getUnitKey(UnitType.Pds, "gray")));
        }

        @Test
        void unknownPlanetArgFallsBackToContextHolder() {
            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!plastic 2 infantry notaplanet"), systemTile, Constants.SPACE, true);
            UnitKey key = Units.getUnitKey(UnitType.Infantry, "red");
            assertEquals(2, systemTile.getUnitHolders().get(Constants.SPACE).getUnitCount(key));
        }

        @Test
        void atLocationOverrideRedirectsToOtherTile() {
            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!plastic 3 fighter @002"), systemTile, Constants.SPACE, true);
            UnitKey key = Units.getUnitKey(UnitType.Fighter, "red");
            assertEquals(3, remoteTile.getUnitHolders().get(Constants.SPACE).getUnitCount(key));
            assertEquals(0, systemTile.getUnitHolders().get(Constants.SPACE).getUnitCount(key));
        }

        @Test
        void unknownAtLocationSkippedSafely() {
            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!plastic 2 infantry @nowhere"), systemTile, Constants.SPACE, true);
            assertEquals(
                    0,
                    systemTile
                            .getUnitHolders()
                            .get(Constants.SPACE)
                            .getUnitCount(Units.getUnitKey(UnitType.Infantry, "red")));
        }
    }

    // -----------------------------------------------------------------------
    // 5. Token placement
    // -----------------------------------------------------------------------

    @Nested
    class TokenEffects {

        @Test
        void shortNameResolvesToFilenameAndNotStoredRaw() {
            // Exercises Mapper.getTokenID — breaks if the token naming convention changes
            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!token gravityrift"), systemTile, Constants.SPACE, true);
            var tokens = systemTile.getUnitHolders().get(Constants.SPACE).getTokenList();
            assertTrue(tokens.contains("token_gravityrift.png"), "resolved filename must be present");
            assertFalse(tokens.contains("gravityrift"), "raw short name must not be stored");
        }

        @Test
        void unknownTokenStoredAsIs() {
            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!token custom_token_xyz.png"), systemTile, Constants.SPACE, true);
            assertTrue(systemTile
                    .getUnitHolders()
                    .get(Constants.SPACE)
                    .getTokenList()
                    .contains("custom_token_xyz.png"));
        }

        @Test
        void atLocationOverrideRedirectsToken() {
            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!token gravityrift @002"), systemTile, Constants.SPACE, true);
            assertTrue(remoteTile
                    .getUnitHolders()
                    .get(Constants.SPACE)
                    .getTokenList()
                    .contains("token_gravityrift.png"));
            assertFalse(systemTile
                    .getUnitHolders()
                    .get(Constants.SPACE)
                    .getTokenList()
                    .contains("token_gravityrift.png"));
        }
    }

    // -----------------------------------------------------------------------
    // 6. Effect descriptions (player changes + map changes)
    // -----------------------------------------------------------------------

    @Nested
    class EffectDescriptions {

        @Test
        void playerChangesDescribed() {
            var descs = LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!tg +2", "!fleet +1", "!tactic +1"), systemTile, Constants.SPACE, true);
            assertEquals(3, descs.size());
            assertTrue(descs.get(0).contains("trade good"));
            assertTrue(descs.get(1).contains("fleet CC"));
            assertTrue(descs.get(2).contains("tactic CC"));
        }

        @Test
        void gainedVsLostWording() {
            var descs = LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!tg +3", "!fleet -1"), systemTile, Constants.SPACE, true);
            assertTrue(descs.get(0).contains("gained"));
            assertTrue(descs.get(1).contains("lost"));
        }

        @Test
        void tradeGoodsPluralizedCorrectly() {
            var plural = LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!tg +2"), systemTile, Constants.SPACE, true);
            assertTrue(plural.get(0).contains("trade goods"));

            var singular = LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!tg -1"), systemTile, Constants.SPACE, true);
            assertTrue(
                    singular.get(0).contains("trade good") && !singular.get(0).contains("trade goods"));
        }

        @Test
        void plasticAndTokenDescribed() {
            var descs = LoreEffects.applyLoreEffectsForTest(
                    player,
                    game,
                    entry("!plastic 2 infantry", "!token gravityrift"),
                    systemTile,
                    Constants.SPACE,
                    true);
            assertEquals(2, descs.size());
            assertTrue(descs.get(0).contains("2")
                    && descs.get(0).contains("red")
                    && descs.get(0).contains("infantry"));
            assertTrue(descs.get(1).contains("gravityrift"));
        }

        @Test
        void acDescribed() {
            var descs = LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!ac 2"), systemTile, Constants.SPACE, true);
            assertEquals(1, descs.size());
            assertTrue(descs.get(0).contains("action card"));
        }

        @Test
        void commsDescribed() {
            var descs = LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!comms +2"), systemTile, Constants.SPACE, true);
            assertEquals(1, descs.size());
            assertTrue(descs.get(0).contains("commodity"));
        }
    }

    // -----------------------------------------------------------------------
    // 7. Remove-unit effects
    // -----------------------------------------------------------------------

    @Nested
    class RemoveUnitEffects {

        private UnitKey redInfantry;

        @BeforeEach
        void seedUnits() {
            redInfantry = Units.getUnitKey(UnitType.Infantry, "red");
            systemTile.addUnit(Constants.SPACE, redInfantry, 3);
        }

        @Test
        void removesRequestedCount() {
            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!removeunit 2 infantry"), systemTile, Constants.SPACE, true);
            assertEquals(1, systemTile.getUnitHolders().get(Constants.SPACE).getUnitCount(redInfantry));
        }

        @Test
        void overRemovalClampsToZero() {
            // documented behavior: removes whatever exists if fewer are present — silent partial removal
            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!removeunit 5 infantry"), systemTile, Constants.SPACE, true);
            assertEquals(0, systemTile.getUnitHolders().get(Constants.SPACE).getUnitCount(redInfantry));
        }

        @Test
        void neutralColorRemoved() {
            UnitKey grayFighter = Units.getUnitKey(UnitType.Fighter, "gray");
            systemTile.addUnit(Constants.SPACE, grayFighter, 2);
            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!removeunit neutral 1 fighter"), systemTile, Constants.SPACE, true);
            assertEquals(1, systemTile.getUnitHolders().get(Constants.SPACE).getUnitCount(grayFighter));
        }

        @Test
        void removesFromPlanetHolderOnly() {
            systemTile.addUnit("mr", redInfantry, 2);
            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!removeunit 1 infantry mr"), systemTile, Constants.SPACE, true);
            assertEquals(1, systemTile.getUnitHolders().get("mr").getUnitCount(redInfantry));
            // space holder is untouched
            assertEquals(3, systemTile.getUnitHolders().get(Constants.SPACE).getUnitCount(redInfantry));
        }

        @Test
        void describedAsMapChange() {
            var descs = LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!removeunit 2 infantry"), systemTile, Constants.SPACE, true);
            assertEquals(1, descs.size());
            assertTrue(descs.get(0).contains("Removed")
                    && descs.get(0).contains("red")
                    && descs.get(0).contains("infantry"));
        }
    }

    // -----------------------------------------------------------------------
    // 8. Remove-token effects
    // -----------------------------------------------------------------------

    @Nested
    class RemoveTokenEffects {

        @Test
        void removesExistingToken() {
            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!token gravityrift"), systemTile, Constants.SPACE, true);
            assertTrue(systemTile
                    .getUnitHolders()
                    .get(Constants.SPACE)
                    .getTokenList()
                    .contains("token_gravityrift.png"));

            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!removetoken gravityrift"), systemTile, Constants.SPACE, true);
            assertFalse(systemTile
                    .getUnitHolders()
                    .get(Constants.SPACE)
                    .getTokenList()
                    .contains("token_gravityrift.png"));
        }

        @Test
        void removingExistingTokenIsDescribedAsMapChange() {
            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!token gravityrift"), systemTile, Constants.SPACE, true);
            var descs = LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!removetoken gravityrift"), systemTile, Constants.SPACE, true);
            assertEquals(1, descs.size());
            assertTrue(descs.get(0).contains("Removed") && descs.get(0).contains("gravityrift"));
        }

        @Test
        void missingTokenReportsNothingRemoved() {
            var descs = LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!removetoken gravityrift"), systemTile, Constants.SPACE, true);
            assertEquals(1, descs.size());
            assertTrue(descs.get(0).contains("nothing removed"));
        }
    }

    // -----------------------------------------------------------------------
    // 9. Swap effects
    // -----------------------------------------------------------------------

    @Nested
    class SwapEffects {

        @Test
        void swapsTwoSystems() {
            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!swap 001 002"), systemTile, Constants.SPACE, true);
            assertEquals("19", game.getTileByPosition("001").getTileID());
            assertEquals("18", game.getTileByPosition("002").getTileID());
        }

        @Test
        void swapDescribedAsMapChange() {
            var descs = LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!swap 001 002"), systemTile, Constants.SPACE, true);
            assertEquals(1, descs.size());
            assertTrue(descs.get(0).contains("001") && descs.get(0).contains("002"));
        }

        @Test
        void samePositionIsNoOp() {
            var descs = LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!swap 001 001"), systemTile, Constants.SPACE, true);
            assertTrue(descs.isEmpty());
            assertEquals("18", game.getTileByPosition("001").getTileID());
        }

        @Test
        void unknownPositionIsNoOp() {
            var descs = LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!swap 001 099"), systemTile, Constants.SPACE, true);
            assertTrue(descs.isEmpty());
            assertEquals("18", game.getTileByPosition("001").getTileID());
        }
    }

    // -----------------------------------------------------------------------
    // 10. VP effects
    // -----------------------------------------------------------------------

    @Nested
    class VpEffects {

        @Test
        void grantsCustomVpUnderNamedObjective() {
            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!vp 1 Ancient Relic"), systemTile, Constants.SPACE, true);
            assertNotNull(game.getRevealedPublicObjectives().get("Ancient Relic"));
            assertTrue(game.didPlayerScoreThisAlready(player.getUserID(), "Ancient Relic"));
        }

        @Test
        void defaultLabelWhenNoneGiven() {
            LoreEffects.applyLoreEffectsForTest(player, game, entry("!vp 2"), systemTile, Constants.SPACE, true);
            assertTrue(game.getRevealedPublicObjectives().containsKey("Lore Reward"));
            assertTrue(game.didPlayerScoreThisAlready(player.getUserID(), "Lore Reward"));
        }

        @Test
        void repeatTriggersReuseOnePoAndScoreOnce() {
            // documented limitation: same label reuses one PO, and a player can't score the same PO twice
            LoreEffects.applyLoreEffectsForTest(player, game, entry("!vp 1 Relic"), systemTile, Constants.SPACE, true);
            Integer firstIndex = game.getRevealedPublicObjectives().get("Relic");
            LoreEffects.applyLoreEffectsForTest(player, game, entry("!vp 1 Relic"), systemTile, Constants.SPACE, true);
            assertEquals(firstIndex, game.getRevealedPublicObjectives().get("Relic"));
            // a single unscore proves the player was added to the score list only once
            assertTrue(game.didPlayerScoreThisAlready(player.getUserID(), "Relic"));
            game.unscorePublicObjective(player.getUserID(), "Relic");
            assertFalse(game.didPlayerScoreThisAlready(player.getUserID(), "Relic"));
        }

        @Test
        void vpDescribedAsPlayerChange() {
            var descs = LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!vp 2 Big Win"), systemTile, Constants.SPACE, true);
            assertEquals(1, descs.size());
            assertTrue(descs.get(0).contains("VP") && descs.get(0).contains("Big Win"));
        }
    }

    // -----------------------------------------------------------------------
    // 11. Secret objective draw effects
    // -----------------------------------------------------------------------

    @Nested
    class SecretObjectiveEffects {

        @Test
        void noArgsDrawsOne() {
            LoreEffects.applyLoreEffectsForTest(player, game, entry("!so"), systemTile, Constants.SPACE, true);
            assertEquals(1, player.getSo());
        }

        @Test
        void countArgDrawsThatMany() {
            LoreEffects.applyLoreEffectsForTest(player, game, entry("!so 2"), systemTile, Constants.SPACE, true);
            assertEquals(2, player.getSo());
        }

        @Test
        void aliasSecretobjectiveDrawsOne() {
            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!secretobjective"), systemTile, Constants.SPACE, true);
            assertEquals(1, player.getSo());
        }

        @Test
        void zeroOrNegativeCountIsNoOp() {
            LoreEffects.applyLoreEffectsForTest(player, game, entry("!so 0"), systemTile, Constants.SPACE, true);
            assertEquals(0, player.getSo());
        }

        @Test
        void soDescribed() {
            var descs = LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!so 2"), systemTile, Constants.SPACE, true);
            assertEquals(1, descs.size());
            assertTrue(descs.get(0).contains("secret objective"));
        }
    }

    // -----------------------------------------------------------------------
    // 12. !choice gating: accept:/reject: tagged effect lines
    // -----------------------------------------------------------------------

    @Nested
    class ChoiceGating {

        @Test
        void choiceMarkerIsDetected() {
            LoreEntry e = entry("!choice", "!tg +2");
            assertTrue(e.isChoiceGated());
        }

        @Test
        void noMarkerMeansNotGated() {
            LoreEntry e = entry("!tg +2");
            assertFalse(e.isChoiceGated());
        }

        @Test
        void markerIsCaseInsensitiveAndHiddenFromDisplay() {
            LoreEntry e = entry("!CHOICE", "Visible footer.");
            assertTrue(e.isChoiceGated());
            assertEquals("Visible footer.", e.getDisplayFooter());
        }

        @Test
        void taggedLinesKeepTagInEffectLinesAndAreHiddenFromDisplay() {
            LoreEntry e = entry("!choice", "accept: !tg +2", "reject: !tg -1", "Visible footer.");
            assertEquals(List.of("accept:tg +2", "reject:tg -1"), e.getEffectLines());
            assertEquals("Visible footer.", e.getDisplayFooter());
        }

        @Test
        void tagWithoutSpaceAfterColonAlsoWorks() {
            LoreEntry e = entry("!choice", "accept:!tg +2");
            assertEquals(List.of("accept:tg +2"), e.getEffectLines());
        }

        @Test
        void acceptBranchAppliesAcceptAndAlwaysLinesOnly() {
            LoreEffects.applyLoreEffectsForTest(
                    player,
                    game,
                    entry("!choice", "accept: !tg +2", "reject: !tg -1", "!fleet +1"),
                    systemTile,
                    Constants.SPACE,
                    true,
                    "accept");
            assertEquals(7, player.getTg());
            assertEquals(4, player.getFleetCC());
        }

        @Test
        void rejectBranchAppliesRejectAndAlwaysLinesOnly() {
            LoreEffects.applyLoreEffectsForTest(
                    player,
                    game,
                    entry("!choice", "accept: !tg +2", "reject: !tg -1", "!fleet +1"),
                    systemTile,
                    Constants.SPACE,
                    true,
                    "reject");
            assertEquals(4, player.getTg());
            assertEquals(4, player.getFleetCC());
        }

        @Test
        void noBranchAppliesOnlyAlwaysLines() {
            LoreEffects.applyLoreEffectsForTest(
                    player,
                    game,
                    entry("!choice", "accept: !tg +2", "reject: !tg -1", "!fleet +1"),
                    systemTile,
                    Constants.SPACE,
                    true);
            assertEquals(5, player.getTg());
            assertEquals(4, player.getFleetCC());
        }

        @Test
        void taggedLineWithoutChoiceMarkerIsFlagged() {
            assertFalse(
                    LoreEffects.validateEffects(entry("accept: !tg +2"), game).isEmpty());
        }

        @Test
        void choiceWithWinnerReceiverIsFlagged() {
            LoreEntry e = entry("!choice", "accept: !tg +2");
            e.receiver = LoreService.RECEIVER.WINNER;
            assertFalse(LoreEffects.validateEffects(e, game).isEmpty());
        }

        @Test
        void choiceWithGmReceiverIsFlagged() {
            LoreEntry e = entry("!choice", "accept: !tg +2");
            e.receiver = LoreService.RECEIVER.GM;
            assertFalse(LoreEffects.validateEffects(e, game).isEmpty());
        }

        @Test
        void choiceWithCurrentReceiverIsClean() {
            LoreEntry e = entry("!choice", "accept: !tg +2");
            assertTrue(LoreEffects.validateEffects(e, game).isEmpty());
        }
    }

    // -----------------------------------------------------------------------
    // 12b. Roll gating: !roll NdM gates an entry behind a single "Roll" button;
    //      N-M:/N:-tagged lines only fire when the rolled total lands in that bin.
    // -----------------------------------------------------------------------

    @Nested
    class RollGating {

        @Test
        void rollMarkerIsDetectedAndSpecParsed() {
            LoreEntry e = entry("!roll 2d10", "!tg +2");
            assertTrue(e.isRollGated());
            assertArrayEquals(new int[] {2, 10}, e.getRollSpec());
        }

        @Test
        void noMarkerMeansNotRollGated() {
            LoreEntry e = entry("!tg +2");
            assertFalse(e.isRollGated());
            assertNull(e.getRollSpec());
        }

        @Test
        void markerIsCaseInsensitiveAndHiddenFromDisplay() {
            LoreEntry e = entry("!ROLL 1d20", "Visible footer.");
            assertTrue(e.isRollGated());
            assertEquals("Visible footer.", e.getDisplayFooter());
        }

        @Test
        void binTaggedLinesKeepTagInEffectLinesAndAreHiddenFromDisplay() {
            LoreEntry e = entry("!roll 2d10", "2-10: !tg 1", "11-16: !ac 1", "17-20: !tg 1 !ac 1", "Visible footer.");
            assertEquals(List.of("2-10:tg 1", "11-16:ac 1", "17-20:tg 1", "17-20:ac 1"), e.getEffectLines());
            assertEquals("Visible footer.", e.getDisplayFooter());
        }

        @Test
        void singleValueBinTagWorks() {
            LoreEntry e = entry("!roll 1d6", "6: !tg 1");
            assertEquals(List.of("6:tg 1"), e.getEffectLines());
        }

        @Test
        void tagWithoutSpaceAfterColonAlsoWorks() {
            LoreEntry e = entry("!roll 2d10", "2-10:!tg 1");
            assertEquals(List.of("2-10:tg 1"), e.getEffectLines());
        }

        @Test
        void resolveRollBranchFindsTheBinContainingTheTotal() {
            List<String> lines = List.of("2-10:tg 1", "11-16:ac 1", "17-20:tg 1", "17-20:ac 1");
            assertEquals("2-10", LoreEffects.resolveRollBranch(lines, 7));
            assertEquals("11-16", LoreEffects.resolveRollBranch(lines, 11));
            assertEquals("11-16", LoreEffects.resolveRollBranch(lines, 16));
            assertEquals("17-20", LoreEffects.resolveRollBranch(lines, 20));
        }

        @Test
        void resolveRollBranchReturnsNullWhenNoBinCoversTheTotal() {
            List<String> lines = List.of("2-10:tg 1");
            assertNull(LoreEffects.resolveRollBranch(lines, 15));
        }

        @Test
        void resolveRollBranchIgnoresAcceptRejectTags() {
            List<String> lines = List.of("accept:tg 1", "reject:tg -1", "2-10:ac 1");
            assertEquals("2-10", LoreEffects.resolveRollBranch(lines, 5));
            assertNull(LoreEffects.resolveRollBranch(lines, 99));
        }

        @Test
        void firstMatchingBinWinsOnOverlap() {
            List<String> lines = List.of("1-10:tg 1", "5-15:ac 1");
            assertEquals("1-10", LoreEffects.resolveRollBranch(lines, 7));
        }

        @Test
        void matchingBranchAppliesItsLinesPlusAlwaysLines() {
            LoreEffects.applyLoreEffectsForTest(
                    player,
                    game,
                    entry("!roll 2d10", "2-10: !tg 1", "11-16: !ac 1", "17-20: !tg 1 !ac 1", "!fleet +1"),
                    systemTile,
                    Constants.SPACE,
                    true,
                    "17-20");
            assertEquals(6, player.getTg());
            assertEquals(4, player.getFleetCC());
        }

        @Test
        void nonMatchingBranchAppliesOnlyAlwaysLines() {
            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!roll 2d10", "2-10: !tg 1", "!fleet +1"), systemTile, Constants.SPACE, true);
            assertEquals(5, player.getTg());
            assertEquals(4, player.getFleetCC());
        }

        @Test
        void binTagWithoutRollMarkerIsFlagged() {
            assertFalse(LoreEffects.validateEffects(entry("2-10: !tg +2"), game).isEmpty());
        }

        @Test
        void backwardsBinRangeIsFlagged() {
            assertFalse(LoreEffects.validateEffects(entry("!roll 2d10", "10-2: !tg +2"), game)
                    .isEmpty());
        }

        @Test
        void rollWithChoiceMarkerTooIsFlagged() {
            assertFalse(LoreEffects.validateEffects(entry("!choice", "!roll 2d10"), game)
                    .isEmpty());
        }

        @Test
        void rollWithWinnerReceiverIsFlagged() {
            LoreEntry e = entry("!roll 2d10", "2-10: !tg +2");
            e.receiver = LoreService.RECEIVER.WINNER;
            assertFalse(LoreEffects.validateEffects(e, game).isEmpty());
        }

        @Test
        void rollWithGmReceiverIsFlagged() {
            LoreEntry e = entry("!roll 2d10", "2-10: !tg +2");
            e.receiver = LoreService.RECEIVER.GM;
            assertFalse(LoreEffects.validateEffects(e, game).isEmpty());
        }

        @Test
        void zeroDiceOrOneSidedDieIsFlagged() {
            assertFalse(LoreEffects.validateEffects(entry("!roll 0d10", "2-10: !tg +2"), game)
                    .isEmpty());
            assertFalse(LoreEffects.validateEffects(entry("!roll 2d1", "1: !tg +2"), game)
                    .isEmpty());
        }

        @Test
        void rollWithCurrentReceiverAndValidBinsIsClean() {
            LoreEntry e = entry("!roll 2d10", "2-10: !tg 1", "11-16: !ac 1", "17-20: !tg 1 !ac 1");
            assertTrue(LoreEffects.validateEffects(e, game).isEmpty());
        }
    }

    // -----------------------------------------------------------------------
    // 13. Export/import round-trip (LoreEntry#toString / #fromString, the format
    //     written verbatim to the export file and parsed back on import)
    // -----------------------------------------------------------------------

    @Nested
    class ExportImportRoundTrip {

        @Test
        void simpleEntryRoundTrips() {
            LoreEntry e = entry("Visible footer.");
            e.target = "001";
            e.receiver = LoreService.RECEIVER.ADJACENT;
            e.trigger = LoreService.TRIGGER.MOVED;
            e.ping = LoreService.PING.YES;
            e.persistance = LoreService.PERSISTANCE.ALWAYS;

            LoreEntry parsed = LoreEntry.fromString(e.toString());

            assertEquals(e.target, parsed.target);
            assertEquals(e.loreText, parsed.loreText);
            assertEquals(e.footerText, parsed.footerText);
            assertEquals(e.receiver, parsed.receiver);
            assertEquals(e.trigger, parsed.trigger);
            assertEquals(e.ping, parsed.ping);
            assertEquals(e.persistance, parsed.persistance);
        }

        @Test
        void oncePerPlayerPersistanceRoundTrips() {
            LoreEntry e = entry("!tg +2");
            e.target = "mr";
            e.persistance = LoreService.PERSISTANCE.ONCE_PER_PLAYER;

            LoreEntry parsed = LoreEntry.fromString(e.toString());
            assertEquals(LoreService.PERSISTANCE.ONCE_PER_PLAYER, parsed.persistance);
        }

        @Test
        void choiceGatedFooterWithMultipleLinesRoundTrips() {
            LoreEntry e = entry("!choice", "accept: !tg +2", "reject: !fleet -1", "Some flavor.");
            e.target = "001";

            LoreEntry parsed = LoreEntry.fromString(e.toString());

            assertEquals(e.footerText, parsed.footerText);
            assertTrue(parsed.isChoiceGated());
            assertEquals(e.getEffectLines(), parsed.getEffectLines());
            assertEquals(e.getDisplayFooter(), parsed.getDisplayFooter());
        }

        @Test
        void multipleEntriesJoinedByPipeSplitBackCleanly() {
            // Mirrors LoreService#readLore: entries are toString()'d and joined with "|",
            // and parsed back by splitting on "|" then LoreEntry.fromString.
            LoreEntry e1 = entry("!tg +1");
            e1.target = "001";
            LoreEntry e2 = entry("!fleet +1");
            e2.target = "mr";
            e2.persistance = LoreService.PERSISTANCE.ONCE_PER_PLAYER;

            String exported = e1 + "|" + e2;
            String[] parts = exported.split("\\|");
            assertEquals(2, parts.length);

            LoreEntry parsed1 = LoreEntry.fromString(parts[0]);
            LoreEntry parsed2 = LoreEntry.fromString(parts[1]);
            assertEquals("001", parsed1.target);
            assertEquals("mr", parsed2.target);
            assertEquals(LoreService.PERSISTANCE.ONCE_PER_PLAYER, parsed2.persistance);
        }

        @Test
        void cleanStripsDelimitersThatWouldCorruptTheFormat() {
            // The modal-save path runs user input through LoreService#clean before it's ever
            // stored, which is what keeps free-text from colliding with the ";"/"|" delimiters.
            assertEquals("no delimiters here", LoreService.clean("no; delimiters| here"));
        }

        @Test
        void addLoreFromStringRoundTripsThroughGameStorage() {
            LoreEntry e = entry("!choice", "accept: !tg +2");
            e.target = "001";
            e.persistance = LoreService.PERSISTANCE.ONCE_PER_PLAYER;

            LoreService.addLoreFromString(e.toString(), game);

            LoreEntry stored = LoreService.getGameLore(game).get("001");
            assertNotNull(stored);
            assertTrue(stored.isChoiceGated());
            assertEquals(LoreService.PERSISTANCE.ONCE_PER_PLAYER, stored.persistance);
            assertEquals(List.of("accept:tg +2"), stored.getEffectLines());
        }
    }

    // -----------------------------------------------------------------------
    // 14. Import error reporting: bad entries are skipped individually, with a
    //     clear reason and position, instead of aborting the whole import
    // -----------------------------------------------------------------------

    @Nested
    class ImportErrorReporting {

        // PositionMapper's real position keys are "000", "101", "102"... — distinct from the "001"
        // used elsewhere in this file as an arbitrary Tile-map key. validateLore (used by import)
        // checks PositionMapper, so these tests need an actual recognized position.
        @BeforeEach
        void registerValidSystemPosition() {
            game.setTile(new Tile("18", "000"));
        }

        @Test
        void validEntriesAllImportWithNoErrors() {
            LoreEntry e1 = entry("!tg +1");
            e1.target = "000";
            LoreEntry e2 = entry("!fleet +1");
            e2.target = "mr";

            LoreService.ImportResult result = LoreService.parseLoreImport(e1 + "|" + e2, game);

            assertEquals(2, result.entries().size());
            assertTrue(result.errors().isEmpty());
        }

        @Test
        void malformedEntryIsSkippedWithPositionAndReason() {
            LoreEntry good = entry("!tg +1");
            good.target = "000";

            LoreService.ImportResult result = LoreService.parseLoreImport("no semicolons here|" + good, game);

            assertEquals(1, result.entries().size());
            assertEquals(1, result.errors().size());
            assertTrue(result.errors().get(0).contains("entry #1"));
            assertTrue(result.errors().get(0).contains("malformed"));
        }

        @Test
        void invalidTargetIsSkippedButOtherEntriesStillImport() {
            LoreEntry bad = entry("!tg +1");
            bad.target = "999"; // not a tile on this game's map
            LoreEntry good = entry("!fleet +1");
            good.target = "000";

            LoreService.ImportResult result = LoreService.parseLoreImport(bad + "|" + good, game);

            assertEquals(1, result.entries().size());
            assertTrue(result.entries().containsKey("000"));
            assertEquals(1, result.errors().size());
            assertTrue(result.errors().get(0).contains("entry #1"));
            assertTrue(result.errors().get(0).contains("999"));
        }

        @Test
        void unrecognizedEnumNamesTheBadFieldAndAreSkipped() {
            LoreService.ImportResult result =
                    LoreService.parseLoreImport("000;Some lore.;;NOTAREALRECEIVER;CONTROLLED;NO;ONCE", game);

            assertTrue(result.entries().isEmpty());
            assertEquals(1, result.errors().size());
            assertTrue(result.errors().get(0).contains("RECEIVER"));
            assertTrue(result.errors().get(0).contains("NOTAREALRECEIVER"));
        }

        @Test
        void effectWarningsDoNotPreventImport() {
            LoreEntry e = entry("!notarealverb");
            e.target = "000";

            LoreService.ImportResult result = LoreService.parseLoreImport(e.toString(), game);

            assertEquals(1, result.entries().size());
            assertTrue(result.errors().isEmpty());
            assertFalse(result.warnings().isEmpty());
        }

        @Test
        void blankSegmentsFromStrayPipesAreTolerated() {
            LoreEntry e = entry("!tg +1");
            e.target = "000";

            LoreService.ImportResult result = LoreService.parseLoreImport("|" + e + "||", game);

            assertEquals(1, result.entries().size());
            assertTrue(result.errors().isEmpty());
        }
    }

    // -----------------------------------------------------------------------
    // 15. Multi-recipient delivery: ADJACENT/ALL give every recipient their own
    //     player-stat reward, but map-mutating effects fire exactly once
    //     (regression test for the original single-recipient bug).
    // -----------------------------------------------------------------------

    @Nested
    class MultiRecipientDelivery {

        private Player otherPlayer;

        @BeforeEach
        void addSecondRealPlayer() {
            otherPlayer = addRealPlayer("other-user-id", "jolnar", "blue");
            otherPlayer.setTg(0);
        }

        @Test
        void allReceiverGivesEveryRealPlayerTheirOwnStatReward() {
            LoreEntry e = entry("!tg +2");
            e.receiver = LoreService.RECEIVER.ALL;
            LoreService.deliverLore(player, game, "001", true, e, "001");

            assertEquals(7, player.getTg());
            assertEquals(2, otherPlayer.getTg());
        }

        @Test
        void allReceiverAppliesMapEffectOnlyOnceAttributedToTriggeringPlayer() {
            LoreEntry e = entry("!tg +1", "!plastic 1 infantry");
            e.target = "001";
            e.receiver = LoreService.RECEIVER.ALL;
            LoreService.deliverLore(player, game, "001", true, e, "001");

            assertEquals(
                    1,
                    systemTile
                            .getUnitHolders()
                            .get(Constants.SPACE)
                            .getUnitCount(Units.getUnitKey(UnitType.Infantry, "red")));
            assertEquals(
                    0,
                    systemTile
                            .getUnitHolders()
                            .get(Constants.SPACE)
                            .getUnitCount(Units.getUnitKey(UnitType.Infantry, "blue")));
            assertEquals(6, player.getTg());
            assertEquals(1, otherPlayer.getTg());
        }

        @Test
        void onceEntryIsRemovedAfterDirectDelivery() {
            LoreEntry e = entry("!tg +1");
            e.persistance = LoreService.PERSISTANCE.ONCE;
            LoreService.getGameLore(game).put("001", e);

            LoreService.deliverLore(player, game, "001", true, e, "001");

            assertFalse(LoreService.getGameLore(game).containsKey("001"));
        }

        @Test
        void oncePerPlayerBlocksRedeliveryToSamePlayerButNotOthers() {
            LoreEntry e = entry("!tg +1");
            e.persistance = LoreService.PERSISTANCE.ONCE_PER_PLAYER;
            LoreService.getGameLore(game).put("001", e);

            LoreService.showLore(player, game, "001", true);
            assertEquals(6, player.getTg());

            player.setTg(6); // sanity baseline before the repeat attempt
            LoreService.showLore(player, game, "001", true);
            assertEquals(6, player.getTg(), "second delivery to the same player must be a no-op");

            LoreService.showLore(otherPlayer, game, "001", true);
            assertEquals(1, otherPlayer.getTg(), "a different player must still receive the reward");
        }
    }

    // -----------------------------------------------------------------------
    // 16. Choice-gated delivery across multiple resolvers: map-mutating effects
    //     fire exactly once for the whole entry, and PERSISTANCE.ALWAYS lets the
    //     choice be offered again once every current-round resolver has answered.
    // -----------------------------------------------------------------------

    @Nested
    class ChoiceGatedMultiResolverDelivery {

        private Player otherPlayer;
        private LoreEntry choiceEntry;

        @BeforeEach
        void setUpChoiceEntry() {
            otherPlayer = addRealPlayer("other-user-id", "jolnar", "blue");
            otherPlayer.setTg(0);
            choiceEntry = entry("!choice", "!plastic 1 infantry", "accept: !tg +2", "reject: !tg -1");
            choiceEntry.target = "001";
            choiceEntry.receiver = LoreService.RECEIVER.ALL;
        }

        /** Mirrors what the (button-driven) {@code handleChoiceConfirmation} does before delivering. */
        private void resolve(Player resolver, String branch) {
            LoreService.addStoredId(game, LoreService.choiceResolvedKey("001"), resolver.getUserID());
            LoreService.deliverChoiceLore(resolver, game, "001", true, choiceEntry, "001", branch);
        }

        @Test
        void mapEffectAppliesOnceAcrossResolvers() {
            choiceEntry.persistance = LoreService.PERSISTANCE.ONCE;
            LoreService.getGameLore(game).put("001", choiceEntry);

            resolve(player, "accept");
            resolve(otherPlayer, "accept");

            assertEquals(
                    1,
                    systemTile
                            .getUnitHolders()
                            .get(Constants.SPACE)
                            .getUnitCount(Units.getUnitKey(UnitType.Infantry, "red")));
            assertEquals(7, player.getTg());
            assertEquals(2, otherPlayer.getTg());
        }

        @Test
        void onceEntryRemovedOnlyAfterWholeAudienceResolves() {
            choiceEntry.persistance = LoreService.PERSISTANCE.ONCE;
            LoreService.getGameLore(game).put("001", choiceEntry);

            resolve(player, "accept");
            assertTrue(LoreService.getGameLore(game).containsKey("001"), "must wait for the rest of the audience");

            resolve(otherPlayer, "accept");
            assertFalse(LoreService.getGameLore(game).containsKey("001"));
        }

        @Test
        void alwaysEntryStaysAndBookkeepingClearsOnceAudienceResolves() {
            choiceEntry.persistance = LoreService.PERSISTANCE.ALWAYS;
            LoreService.getGameLore(game).put("001", choiceEntry);
            LoreService.addStoredId(game, LoreService.choiceOfferedKey("001"), player.getUserID());
            LoreService.addStoredId(game, LoreService.choiceOfferedKey("001"), otherPlayer.getUserID());

            resolve(player, "accept");
            resolve(otherPlayer, "reject");

            assertTrue(LoreService.getGameLore(game).containsKey("001"), "ALWAYS entries are never removed");
            assertTrue(LoreService.getStoredIdSet(game, LoreService.choiceOfferedKey("001"))
                    .isEmpty());
            assertTrue(LoreService.getStoredIdSet(game, LoreService.choiceResolvedKey("001"))
                    .isEmpty());
            assertTrue(
                    game.getStoredValue(LoreService.choiceMapAppliedKey("001")).isEmpty());
        }

        @Test
        void alwaysEntryCanBeOfferedAgainAfterRoundClears() {
            choiceEntry.persistance = LoreService.PERSISTANCE.ALWAYS;
            LoreService.getGameLore(game).put("001", choiceEntry);
            LoreService.addStoredId(game, LoreService.choiceOfferedKey("001"), player.getUserID());
            LoreService.addStoredId(game, LoreService.choiceOfferedKey("001"), otherPlayer.getUserID());

            resolve(player, "accept");
            resolve(otherPlayer, "accept");

            // Bookkeeping cleared by the round completing — a fresh round can now be offered.
            LoreService.requestChoiceConfirmation(player, game, "001", true, choiceEntry, "001");
            assertTrue(LoreService.getStoredIdSet(game, LoreService.choiceOfferedKey("001"))
                    .contains(player.getUserID()));
        }

        @Test
        void rejectBranchSkipsAcceptOnlyEffectsButStillAppliesMapEffectOnce() {
            choiceEntry.persistance = LoreService.PERSISTANCE.ONCE;
            LoreService.getGameLore(game).put("001", choiceEntry);

            resolve(player, "reject");

            assertEquals(
                    1,
                    systemTile
                            .getUnitHolders()
                            .get(Constants.SPACE)
                            .getUnitCount(Units.getUnitKey(UnitType.Infantry, "red")));
            assertEquals(4, player.getTg());
        }
    }

    // -----------------------------------------------------------------------
    // 16b. Roll-gated delivery: mirrors ChoiceGatedMultiResolverDelivery, but the
    //      "branch" each resolver lands on comes from their own dice roll instead
    //      of an accept/reject pick.
    // -----------------------------------------------------------------------

    @Nested
    class RollGatedDelivery {

        private Player otherPlayer;
        private LoreEntry rollEntry;

        @BeforeEach
        void setUpRollEntry() {
            otherPlayer = addRealPlayer("other-user-id", "jolnar", "blue");
            otherPlayer.setTg(0);
            rollEntry = entry("!roll 2d10", "!plastic 1 infantry", "2-10: !tg +2", "11-20: !tg +5");
            rollEntry.target = "001";
            rollEntry.receiver = LoreService.RECEIVER.ALL;
        }

        /** Mirrors what the (button-driven) {@code handleRollButton} does before delivering. */
        private void resolve(Player resolver, int total) {
            LoreService.addStoredId(game, LoreService.choiceResolvedKey("001"), resolver.getUserID());
            String branch = LoreEffects.resolveRollBranch(rollEntry.getEffectLines(), total);
            LoreService.deliverRollLore(resolver, game, "001", true, rollEntry, "001", total, branch);
        }

        @Test
        void mapEffectAppliesOnceAcrossResolvers() {
            rollEntry.persistance = LoreService.PERSISTANCE.ONCE;
            LoreService.getGameLore(game).put("001", rollEntry);

            resolve(player, 5);
            resolve(otherPlayer, 15);

            assertEquals(
                    1,
                    systemTile
                            .getUnitHolders()
                            .get(Constants.SPACE)
                            .getUnitCount(Units.getUnitKey(UnitType.Infantry, "red")));
            assertEquals(7, player.getTg());
            assertEquals(5, otherPlayer.getTg());
        }

        @Test
        void onceEntryRemovedOnlyAfterWholeAudienceResolves() {
            rollEntry.persistance = LoreService.PERSISTANCE.ONCE;
            LoreService.getGameLore(game).put("001", rollEntry);

            resolve(player, 5);
            assertTrue(LoreService.getGameLore(game).containsKey("001"), "must wait for the rest of the audience");

            resolve(otherPlayer, 15);
            assertFalse(LoreService.getGameLore(game).containsKey("001"));
        }

        @Test
        void rollOutsideEveryBinGrantsNoBinRewardButStillCompletesTheRound() {
            rollEntry.persistance = LoreService.PERSISTANCE.ONCE;
            rollEntry.receiver = LoreService.RECEIVER.CURRENT; // single-person audience for this test
            rollEntry.footerText = "!roll 2d10\n!fleet +1\n2-5: !tg +2";
            LoreService.getGameLore(game).put("001", rollEntry);

            resolve(player, 10); // outside the only bin (2-5)

            assertEquals(5, player.getTg(), "no bin matched, so no tg change");
            assertEquals(4, player.getFleetCC(), "the always-fire line still applies");
            assertFalse(LoreService.getGameLore(game).containsKey("001"), "round still completes and clears ONCE");
        }

        @Test
        void requestRollConfirmationOffersEveryAudienceMemberExactlyOnce() {
            rollEntry.persistance = LoreService.PERSISTANCE.ONCE;
            LoreService.getGameLore(game).put("001", rollEntry);

            LoreService.requestRollConfirmation(player, game, "001", true, rollEntry, "001");

            Set<String> offered = LoreService.getStoredIdSet(game, LoreService.choiceOfferedKey("001"));
            assertTrue(offered.contains(player.getUserID()));
            assertTrue(offered.contains(otherPlayer.getUserID()));
        }
    }

    // -----------------------------------------------------------------------
    // 17. NPC-controlled players never get interactive buttons: they're excluded
    //     from choice audiences, and the WINNER/LOSER self-report prompt is skipped.
    // -----------------------------------------------------------------------

    @Nested
    class NpcButtonSuppression {

        private Player npcPlayer;

        @BeforeEach
        void addNpcPlayer() {
            npcPlayer = addRealPlayer("npc-user-id", "sol", "yellow");
            npcPlayer.setNpc(true);
        }

        @Test
        void computeChoiceAudienceExcludesNpcs() {
            LoreEntry e = entry("!choice", "accept: !tg +1");
            e.receiver = LoreService.RECEIVER.ALL;

            List<Player> audience = LoreService.computeChoiceAudience(game, e, player, "001");

            assertTrue(audience.contains(player));
            assertFalse(audience.contains(npcPlayer), "NPC seats must not be offered interactive choices");
        }

        @Test
        void requestChoiceConfirmationNeverOffersAnNpc() {
            LoreEntry e = entry("!choice", "accept: !tg +1");
            e.receiver = LoreService.RECEIVER.ALL;

            LoreService.requestChoiceConfirmation(player, game, "001", true, e, "001");

            var offered = LoreService.getStoredIdSet(game, LoreService.choiceOfferedKey("001"));
            assertTrue(offered.contains(player.getUserID()));
            assertFalse(offered.contains(npcPlayer.getUserID()));
        }

        @Test
        void winnerLoserSelfReportIsSkippedForNpcTriggeringPlayer() {
            LoreEntry e = entry("!tg +1");
            e.receiver = LoreService.RECEIVER.WINNER;
            e.persistance = LoreService.PERSISTANCE.ONCE;
            LoreService.getGameLore(game).put("001", e);

            LoreService.showLore(npcPlayer, game, "001", true);

            // No button was offered, so nothing was ever delivered: the ONCE entry must still be there
            // and the NPC's stats must be untouched.
            assertTrue(LoreService.getGameLore(game).containsKey("001"));
            assertEquals(0, npcPlayer.getTg());
        }
    }

    // -----------------------------------------------------------------------
    // 18. Player replacement migrates lore tracking state to the new userID,
    //     without touching unrelated stored values that happen to contain the
    //     same substring.
    // -----------------------------------------------------------------------

    @Nested
    class PlayerReplacementMigration {

        private static final String OLD_ID = "old-user-id";
        private static final String NEW_ID = "new-user-id";
        private static final String OTHER_ID = "other-user-id";

        @Test
        void migratesDeliveredOfferedAndResolvedTrackingKeys() {
            game.setStoredValue("loreDeliveredTo_001", OLD_ID + "," + OTHER_ID);
            game.setStoredValue("loreChoiceOffered_002", OLD_ID);
            game.setStoredValue("loreChoiceResolved_003", OLD_ID + "," + OTHER_ID);

            LoreService.onPlayerReplaced(game, OLD_ID, NEW_ID);

            var delivered = LoreService.getStoredIdSet(game, "loreDeliveredTo_001");
            assertTrue(delivered.contains(NEW_ID));
            assertTrue(delivered.contains(OTHER_ID));
            assertFalse(delivered.contains(OLD_ID));

            assertEquals(NEW_ID, game.getStoredValue("loreChoiceOffered_002"));

            var resolved = LoreService.getStoredIdSet(game, "loreChoiceResolved_003");
            assertTrue(resolved.contains(NEW_ID));
            assertTrue(resolved.contains(OTHER_ID));
            assertFalse(resolved.contains(OLD_ID));
        }

        @Test
        void keyWithoutOldIdIsLeftUntouched() {
            game.setStoredValue("loreDeliveredTo_001", OTHER_ID);

            LoreService.onPlayerReplaced(game, OLD_ID, NEW_ID);

            assertEquals(OTHER_ID, game.getStoredValue("loreDeliveredTo_001"));
        }

        @Test
        void unrelatedStoredValueContainingOldIdIsNotTouched() {
            // Same userID substring, but the key isn't one of the lore-tracking prefixes.
            game.setStoredValue("someUnrelatedKey_" + OLD_ID, OLD_ID);

            LoreService.onPlayerReplaced(game, OLD_ID, NEW_ID);

            assertEquals(OLD_ID, game.getStoredValue("someUnrelatedKey_" + OLD_ID));
        }
    }

    // -----------------------------------------------------------------------
    // 19. LoreEntry.copy() produces an independent instance. Saving one set of
    //     lore settings to several comma-separated targets at once relies on
    //     this: a shared, mutated reference left every stored key pointing at
    //     the same entry with whichever target was set last.
    // -----------------------------------------------------------------------

    @Nested
    class LoreEntryCopy {

        @Test
        void copyIsFieldForFieldEqualButIndependent() {
            LoreEntry original = entry("!choice", "accept: !tg +2");
            original.target = "001";
            original.receiver = LoreService.RECEIVER.ADJACENT;
            original.trigger = LoreService.TRIGGER.MOVED;
            original.ping = LoreService.PING.YES;
            original.persistance = LoreService.PERSISTANCE.ONCE_PER_PLAYER;

            LoreEntry clone = original.copy();

            assertEquals(original.toString(), clone.toString());

            // Mutating the clone's target must not bleed back into the original.
            clone.target = "002";
            assertEquals("001", original.target);
            assertEquals("002", clone.target);
        }

        @Test
        void copiesSavedToSeveralTargetsKeepTheirOwnTarget() {
            LoreEntry base = entry("!tg +1");
            for (String target : List.of("001", "002")) {
                LoreEntry forTarget = base.copy();
                forTarget.target = target;
                LoreService.getGameLore(game).put(target, forTarget);
            }

            // Each stored key must hold an entry whose own target matches the key — the multi-target
            // save bug left both keys pointing at one shared object stuck on the last target.
            assertEquals("001", LoreService.getGameLore(game).get("001").target);
            assertEquals("002", LoreService.getGameLore(game).get("002").target);
        }
    }

    // -----------------------------------------------------------------------
    // 20. ONCE_PER_PLAYER is enforced for ADJACENT/ALL choice-gated entries too:
    //     showLore's check only covers the triggering player, so the choice
    //     offering and round-completion paths must also skip already-delivered
    //     audience members (otherwise such an entry behaves like ALWAYS).
    // -----------------------------------------------------------------------

    @Nested
    class OncePerPlayerChoiceAudience {

        private Player otherPlayer;
        private LoreEntry choiceEntry;

        @BeforeEach
        void setUp() {
            otherPlayer = addRealPlayer("other-user-id", "jolnar", "blue");
            otherPlayer.setTg(0);
            choiceEntry = entry("!choice", "accept: !tg +2", "reject: !tg -1");
            choiceEntry.target = "001";
            choiceEntry.receiver = LoreService.RECEIVER.ALL;
            choiceEntry.persistance = LoreService.PERSISTANCE.ONCE_PER_PLAYER;
            LoreService.getGameLore(game).put("001", choiceEntry);
        }

        @Test
        void alreadyDeliveredPlayerIsNotReOffered() {
            LoreService.addStoredId(game, LoreService.loreDeliveredKey("001"), player.getUserID());

            LoreService.requestChoiceConfirmation(player, game, "001", true, choiceEntry, "001");

            var offered = LoreService.getStoredIdSet(game, LoreService.choiceOfferedKey("001"));
            assertFalse(offered.contains(player.getUserID()), "a player who already received it must be skipped");
            assertTrue(offered.contains(otherPlayer.getUserID()), "a player who hasn't received it is still offered");
        }

        @Test
        void alreadyDeliveredPlayerDoesNotBlockRoundCompletion() {
            // player already got it in a prior round; only otherPlayer is offered/resolves this round.
            LoreService.addStoredId(game, LoreService.loreDeliveredKey("001"), player.getUserID());
            LoreService.addStoredId(game, LoreService.choiceOfferedKey("001"), otherPlayer.getUserID());
            LoreService.addStoredId(game, LoreService.choiceResolvedKey("001"), otherPlayer.getUserID());

            LoreService.deliverChoiceLore(otherPlayer, game, "001", true, choiceEntry, "001", "accept");

            // The round should complete (bookkeeping cleared) even though `player` never resolved it,
            // because they're already in loreDeliveredKey and were never re-offered.
            assertTrue(
                    LoreService.getStoredIdSet(game, LoreService.choiceOfferedKey("001"))
                            .isEmpty(),
                    "round must complete despite the already-delivered player not resolving");
            assertTrue(LoreService.getStoredIdSet(game, LoreService.choiceResolvedKey("001"))
                    .isEmpty());
            assertEquals(2, otherPlayer.getTg());
        }
    }

    // -----------------------------------------------------------------------
    // Lore enablement gating (FoW always on; non-FoW opt-in for games created after
    // the gate shipped; games created before that cutoff are grandfathered in by date,
    // since /special2 lore already worked unrestricted in non-FoW games before lore_mode existed)
    // -----------------------------------------------------------------------

    @Nested
    class LoreEnablementGating {

        private static final long BEFORE_CUTOFF =
                java.time.Instant.parse("2025-01-01T00:00:00Z").toEpochMilli();
        private static final long AFTER_CUTOFF =
                java.time.Instant.parse("2026-07-01T00:00:00Z").toEpochMilli();

        @Test
        void fowGameAlwaysEnabledRegardlessOfLoreModeOrCreationDate() {
            game.setFowMode(true);
            game.setCreationDateTime(AFTER_CUTOFF);
            assertTrue(LoreService.isLoreEnabled(game));
        }

        @Test
        void nonFowGameCreatedAfterCutoffWithoutLoreModeIsDisabled() {
            game.setCreationDateTime(AFTER_CUTOFF);
            assertFalse(game.isLoreMode());
            assertFalse(LoreService.isLoreEnabled(game));
        }

        @Test
        void nonFowGameCreatedAfterCutoffWithLoreModeEnabledIsEnabled() {
            game.setCreationDateTime(AFTER_CUTOFF);
            game.setLoreMode(true);
            assertTrue(LoreService.isLoreEnabled(game));
        }

        @Test
        void addingLoreAfterCutoffDoesNotSilentlyBypassTheGate() {
            // Regression: adding lore content must NOT itself flip the gate open —
            // only the explicit lore_mode toggle (or a pre-cutoff creation date) should.
            game.setCreationDateTime(AFTER_CUTOFF);
            game.setStoredValue("fowSystemLore", "001;Some lore;;ALL;ACTIVATED;NO;ONCE");
            assertFalse(game.isLoreMode());
            assertFalse(LoreService.isLoreEnabled(game));
        }
    }

    // -----------------------------------------------------------------------
    // 22. Trigger entry point: showSystemLore is what production actually calls
    //     when a system is activated / moved into — it must scan for matching
    //     entries (including tagged ones), respect the trigger guards, and honor
    //     round windows. Everything above tests showLore/deliverLore directly.
    // -----------------------------------------------------------------------

    @Nested
    class TriggerScan {

        @BeforeEach
        void enableLore() {
            game.setLoreMode(true);
        }

        private LoreEntry seed(String target, LoreService.TRIGGER trigger, String... footerLines) {
            LoreEntry e = entry(footerLines);
            e.target = target;
            e.trigger = trigger;
            e.persistance = LoreService.PERSISTANCE.ALWAYS; // survive repeat fires within a test
            LoreService.getGameLore(game).put(target, e);
            return e;
        }

        @Test
        void activatedEntryFiresThroughTheRealTriggerPath() {
            seed("001", LoreService.TRIGGER.ACTIVATED, "!tg +1");
            LoreService.showSystemLore(player, game, "001", LoreService.TRIGGER.ACTIVATED);
            assertEquals(6, player.getTg());
        }

        @Test
        void triggerMismatchDoesNotFire() {
            seed("001", LoreService.TRIGGER.ACTIVATED, "!tg +1");
            LoreService.showSystemLore(player, game, "001", LoreService.TRIGGER.MOVED);
            assertEquals(5, player.getTg());
        }

        @Test
        void movedTriggerRequiresUnitsInSystem() {
            seed("001", LoreService.TRIGGER.MOVED, "!tg +1");

            LoreService.showSystemLore(player, game, "001", LoreService.TRIGGER.MOVED);
            assertEquals(5, player.getTg(), "no units in system yet — must not fire");

            systemTile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Fighter, "red"), 1);
            LoreService.showSystemLore(player, game, "001", LoreService.TRIGGER.MOVED);
            assertEquals(6, player.getTg());
        }

        @Test
        void roundWindowGatesTheTrigger() {
            LoreEntry e = seed("001", LoreService.TRIGGER.ACTIVATED, "!tg +1");
            e.fromRound = 4;

            game.setRound(2);
            LoreService.showSystemLore(player, game, "001", LoreService.TRIGGER.ACTIVATED);
            assertEquals(5, player.getTg(), "round 2 is before the entry's window");

            game.setRound(4);
            LoreService.showSystemLore(player, game, "001", LoreService.TRIGGER.ACTIVATED);
            assertEquals(6, player.getTg());
        }

        @Test
        void disabledLoreNeverFires() {
            seed("001", LoreService.TRIGGER.ACTIVATED, "!tg +1");
            game.setLoreMode(false);
            LoreService.showSystemLore(player, game, "001", LoreService.TRIGGER.ACTIVATED);
            assertEquals(5, player.getTg());
        }
    }

    // -----------------------------------------------------------------------
    // 23. Round gating: range semantics and — crucially — that pre-round-gating
    //     exports (7 fields, no round columns) still parse as unbounded.
    // -----------------------------------------------------------------------

    @Nested
    class RoundGating {

        @Test
        void rangeBoundsAreInclusiveAndZeroMeansUnbounded() {
            LoreEntry e = entry();
            e.fromRound = 3;
            e.tillRound = 6;
            assertFalse(e.isInRoundRange(2));
            assertTrue(e.isInRoundRange(3));
            assertTrue(e.isInRoundRange(6));
            assertFalse(e.isInRoundRange(7));

            e.fromRound = 4;
            e.tillRound = 0;
            assertFalse(e.isInRoundRange(3));
            assertTrue(e.isInRoundRange(99));

            e.fromRound = 0;
            e.tillRound = 2;
            assertTrue(e.isInRoundRange(1));
            assertFalse(e.isInRoundRange(3));

            e.fromRound = 0;
            e.tillRound = 0;
            assertTrue(e.isInRoundRange(1));
            assertTrue(e.isInRoundRange(99));
        }

        @Test
        void roundFieldsSurviveSerializationRoundTrip() {
            LoreEntry e = entry("!tg +1");
            e.target = "001";
            e.fromRound = 3;
            e.tillRound = 6;

            LoreEntry parsed = LoreEntry.fromString(e.toString());
            assertEquals(3, parsed.fromRound);
            assertEquals(6, parsed.tillRound);
        }

        @Test
        void legacySevenFieldExportParsesAsUnbounded() {
            // Serialized before round-gating existed: no fromRound/tillRound columns.
            LoreEntry parsed = LoreEntry.fromString("001;Some lore;;ALL;ACTIVATED;NO;ONCE");
            assertEquals("001", parsed.target);
            assertEquals(LoreService.PERSISTANCE.ONCE, parsed.persistance);
            assertEquals(0, parsed.fromRound);
            assertEquals(0, parsed.tillRound);
            assertTrue(parsed.isInRoundRange(1));
        }

        @Test
        void copyPreservesRoundBounds() {
            LoreEntry e = entry();
            e.fromRound = 2;
            e.tillRound = 5;
            LoreEntry clone = e.copy();
            assertEquals(2, clone.fromRound);
            assertEquals(5, clone.tillRound);
        }
    }

    // -----------------------------------------------------------------------
    // 23b. Phase triggers: strategy/action/status/agenda pseudo-targets with
    //      PHASE_START/PHASE_END fire on phase transitions with no triggering
    //      player — map effects once, player effects fanned to all real players.
    // -----------------------------------------------------------------------

    @Nested
    class PhaseTriggers {

        private Player otherPlayer;

        @BeforeEach
        void enableLoreAndAddSecondPlayer() {
            game.setLoreMode(true); // non-FoW games only get lore via this explicit toggle
            otherPlayer = addRealPlayer("other-user-id", "jolnar", "blue");
            otherPlayer.setTg(0);
        }

        private LoreEntry seed(String target, LoreService.TRIGGER trigger, String... footerLines) {
            LoreEntry e = entry(footerLines);
            e.target = target;
            e.trigger = trigger;
            e.receiver = LoreService.RECEIVER.ALL;
            e.persistance = LoreService.PERSISTANCE.ALWAYS;
            LoreService.getGameLore(game).put(target, e);
            return e;
        }

        @Test
        void validateLoreAcceptsPhaseTargetsAndCanonicalizesCase() {
            LoreEntry e = entry("!tg +1");
            e.trigger = LoreService.TRIGGER.PHASE_START;
            assertEquals("strategy", LoreService.validateLore("Strategy", "", e, new HashMap<>(), game));
            assertEquals("agenda#Omen", LoreService.validateLore("agenda#Omen", "", e, new HashMap<>(), game));
        }

        @Test
        void validateLoreRejectsMismatchedTargetTriggerPairings() {
            LoreEntry controlled = entry("!tg +1"); // default trigger CONTROLLED
            assertNull(LoreService.validateLore("strategy", "", controlled, new HashMap<>(), game));

            LoreEntry phaseTrigger = entry("!tg +1");
            phaseTrigger.trigger = LoreService.TRIGGER.PHASE_END;
            assertNull(LoreService.validateLore("001", "", phaseTrigger, new HashMap<>(), game));
            assertNull(LoreService.validateLore("mecatolrex", "", phaseTrigger, new HashMap<>(), game));
        }

        @Test
        void phaseStartFansPlayerEffectsToAllRealPlayersAndAppliesMapEffectOnce() {
            seed("strategy", LoreService.TRIGGER.PHASE_START, "!tg +1", "!unit red 1 infantry @001");

            LoreService.showPhaseStartLore(game, "strategy");

            assertEquals(6, player.getTg());
            assertEquals(1, otherPlayer.getTg());
            assertEquals(
                    1,
                    systemTile
                            .getUnitHolders()
                            .get(Constants.SPACE)
                            .getUnitCount(Units.getUnitKey(UnitType.Infantry, "red")));
        }

        @Test
        void loreModeToggleGatesPhaseLoreInNormalGames() {
            seed("strategy", LoreService.TRIGGER.PHASE_START, "!tg +1");
            game.setLoreMode(false);

            LoreService.showPhaseStartLore(game, "strategy");

            assertEquals(5, player.getTg(), "phase lore must not fire in a normal game without the lore toggle");
        }

        @Test
        void phaseEndDerivesFromTheCurrentPhaseAndNormalizesSubStates() {
            seed("status", LoreService.TRIGGER.PHASE_END, "!tg +1");
            game.setPhaseOfGame("statusHomework"); // sub-state must normalize to "status"

            LoreService.showPhaseEndLore(game, "strategy");

            assertEquals(6, player.getTg());
        }

        @Test
        void reassertingTheSamePhaseFiresNothing() {
            seed("status", LoreService.TRIGGER.PHASE_END, "!tg +1");
            seed("status#start", LoreService.TRIGGER.PHASE_START, "!fleet +1");
            game.setPhaseOfGame("statusScoring");

            // e.g. a double-clicked "end action phase" button re-entering while already in status
            LoreService.showPhaseLore(game, "status");

            assertEquals(5, player.getTg());
            assertEquals(3, player.getFleetCC());
        }

        @Test
        void phaseStartFiresOncePerRoundButAgainNextRound() {
            seed("action", LoreService.TRIGGER.PHASE_START, "!tg +1");

            LoreService.showPhaseStartLore(game, "action");
            LoreService.showPhaseStartLore(game, "action");
            assertEquals(6, player.getTg(), "second call in the same round must be deduplicated");

            game.setRound(game.getRound() + 1);
            LoreService.showPhaseStartLore(game, "action");
            assertEquals(7, player.getTg(), "an ALWAYS entry fires again next round");
        }

        @Test
        void oncePersistanceRemovesTheEntryAfterFiring() {
            LoreEntry e = seed("agenda", LoreService.TRIGGER.PHASE_START, "!tg +1");
            e.persistance = LoreService.PERSISTANCE.ONCE;

            LoreService.showPhaseStartLore(game, "agenda");

            assertEquals(6, player.getTg());
            assertFalse(LoreService.getGameLore(game).containsKey("agenda"));
        }

        @Test
        void roundWindowGatesPhaseLore() {
            LoreEntry e = seed("strategy", LoreService.TRIGGER.PHASE_START, "!tg +1");
            e.fromRound = 3;

            LoreService.showPhaseStartLore(game, "strategy");
            assertEquals(5, player.getTg(), "round 1 is outside the 3+ window");

            game.setRound(3);
            LoreService.showPhaseStartLore(game, "strategy");
            assertEquals(6, player.getTg());
        }

        @Test
        void gmReceiverAppliesMapEffectsButNoPlayerRewards() {
            LoreEntry e = seed("action", LoreService.TRIGGER.PHASE_START, "!tg +1", "!unit red 1 infantry @001");
            e.receiver = LoreService.RECEIVER.GM;

            LoreService.showPhaseStartLore(game, "action");

            assertEquals(5, player.getTg());
            assertEquals(0, otherPlayer.getTg());
            assertEquals(
                    1,
                    systemTile
                            .getUnitHolders()
                            .get(Constants.SPACE)
                            .getUnitCount(Units.getUnitKey(UnitType.Infantry, "red")));
        }

        @Test
        void setupAndDraftPhasesNeverFirePhaseLore() {
            seed("strategy", LoreService.TRIGGER.PHASE_START, "!tg +1");
            game.setPhaseOfGame("miltydraft");

            LoreService.showPhaseStartLore(game, "miltydraft");
            LoreService.showPhaseEndLore(game, "strategy");

            assertEquals(5, player.getTg());
        }

        @Test
        void choiceGatedPhaseEntryOffersEveryRealPlayerTheirOwnButtons() {
            seed("strategy", LoreService.TRIGGER.PHASE_START, "!choice", "accept: !tg +2");

            LoreService.showPhaseStartLore(game, "strategy");

            Set<String> offered = LoreService.getStoredIdSet(game, LoreService.choiceOfferedKey("strategy"));
            assertTrue(offered.contains(player.getUserID()));
            assertTrue(offered.contains(otherPlayer.getUserID()));
            assertEquals(5, player.getTg(), "no reward until the choice is resolved");
        }

        @Test
        void phaseEntryFootgunsAreWarnedAtSaveTime() {
            LoreEntry e = entry("!unit 1 infantry", "!token gravityrift @001", "!tg +1");
            e.target = "strategy";
            e.trigger = LoreService.TRIGGER.PHASE_START;
            e.receiver = LoreService.RECEIVER.CURRENT;
            e.persistance = LoreService.PERSISTANCE.ONCE_PER_PLAYER;

            List<String> problems = LoreEffects.validateEffects(e, game);

            assertTrue(problems.stream().anyMatch(p -> p.contains("no single receiving player")), problems.toString());
            assertTrue(problems.stream().anyMatch(p -> p.contains("behaves like `Once`")), problems.toString());
            assertTrue(problems.stream().anyMatch(p -> p.contains("explicit color")), problems.toString());
            assertTrue(problems.stream().anyMatch(p -> p.contains("`unit` needs an `@target`")), problems.toString());
        }

        @Test
        void cleanPhaseEntryValidatesWithoutWarnings() {
            LoreEntry e = entry("!unit red 1 infantry @001", "!tg +1", "!settile 305 41");
            e.target = "strategy";
            e.trigger = LoreService.TRIGGER.PHASE_START;
            e.receiver = LoreService.RECEIVER.ALL;

            assertTrue(LoreEffects.validateEffects(e, game).isEmpty());
        }

        @Test
        void gmReceiverPhaseEntryWarnsAboutPlayerRewardLines() {
            LoreEntry e = entry("!tg +1", "!unit red 1 infantry @001");
            e.target = "action";
            e.trigger = LoreService.TRIGGER.PHASE_END;
            e.receiver = LoreService.RECEIVER.GM;

            List<String> problems = LoreEffects.validateEffects(e, game);

            assertTrue(problems.stream().anyMatch(p -> p.contains("player rewards are skipped")), problems.toString());
            assertFalse(
                    problems.stream().anyMatch(p -> p.contains("explicit color")),
                    "the unit line names its color explicitly");
        }
    }

    // -----------------------------------------------------------------------
    // 24. Tagged targets: several entries can share one system via "base#tag"
    //     keys. Pins the splitTargetKey null-crash found by the first real
    //     build, and the auto-tag naming scheme.
    // -----------------------------------------------------------------------

    @Nested
    class TaggedTargets {

        @Test
        void splitHandlesBareTaggedAndNullKeys() {
            assertEquals("001", LoreService.splitTargetKey("001").base());
            assertNull(LoreService.splitTargetKey("001").tag());

            assertEquals("001", LoreService.splitTargetKey("001#ambush").base());
            assertEquals("ambush", LoreService.splitTargetKey("001#ambush").tag());

            // Regression: entries seeded without a target used to NPE deep in effect application.
            assertNull(LoreService.splitTargetKey(null).base());
        }

        @Test
        void bareAndTaggedEntriesAtTheSameBaseBothFire() {
            game.setLoreMode(true);

            LoreEntry bare = entry("!tg +1");
            bare.target = "001";
            bare.trigger = LoreService.TRIGGER.ACTIVATED;
            LoreService.getGameLore(game).put("001", bare);

            LoreEntry tagged = entry("!fleet +1");
            tagged.target = "001#ambush";
            tagged.trigger = LoreService.TRIGGER.ACTIVATED;
            LoreService.getGameLore(game).put("001#ambush", tagged);

            LoreService.showSystemLore(player, game, "001", LoreService.TRIGGER.ACTIVATED);

            assertEquals(6, player.getTg(), "bare entry must fire");
            assertEquals(4, player.getFleetCC(), "tagged entry at the same base must fire too");
        }

        @Test
        void taggedEntryMapEffectsResolveTheBaseTile() {
            game.setLoreMode(true);

            LoreEntry tagged = entry("!plastic 1 infantry");
            tagged.target = "001#drop";
            tagged.trigger = LoreService.TRIGGER.ACTIVATED;
            LoreService.getGameLore(game).put("001#drop", tagged);

            LoreService.showSystemLore(player, game, "001", LoreService.TRIGGER.ACTIVATED);

            assertEquals(
                    1,
                    systemTile
                            .getUnitHolders()
                            .get(Constants.SPACE)
                            .getUnitCount(Units.getUnitKey(UnitType.Infantry, "red")),
                    "the '#drop' tag must be stripped when resolving the board tile");
        }

        @Test
        void autoTagBuildsTriggerAndRoundDescriptorsAndDodgesCollisions() {
            Map<String, LoreEntry> saved = new HashMap<>();

            LoreEntry e = entry();
            e.trigger = LoreService.TRIGGER.MOVED;
            e.fromRound = 4;
            e.tillRound = 6;
            assertEquals("522#MovedR4to6", LoreService.autoTag("522", e, saved));

            saved.put("522#MovedR4to6", entry());
            assertEquals("522#MovedR4to62", LoreService.autoTag("522", e, saved));

            LoreEntry battle = entry();
            battle.trigger = LoreService.TRIGGER.SPACE_BATTLE;
            assertEquals("522#SpaceBattle", LoreService.autoTag("522", battle, saved));

            LoreEntry open = entry();
            open.trigger = LoreService.TRIGGER.MOVED;
            open.fromRound = 4;
            assertEquals("522#MovedR4plus", LoreService.autoTag("522", open, saved));

            LoreEntry upTo = entry();
            upTo.trigger = LoreService.TRIGGER.MOVED;
            upTo.tillRound = 6;
            assertEquals("522#MovedRupto6", LoreService.autoTag("522", upTo, saved));

            LoreEntry single = entry();
            single.trigger = LoreService.TRIGGER.MOVED;
            single.fromRound = 4;
            single.tillRound = 4;
            assertEquals("522#MovedR4", LoreService.autoTag("522", single, saved));
        }

        @Test
        void validateLoreRespectsExplicitTagsAndAutoTagsCollisions() {
            // validateLore needs a PositionMapper-recognized position with a tile on the board.
            game.setTile(new Tile("18", "000"));
            Map<String, LoreEntry> saved = new HashMap<>();
            LoreEntry e = entry("!tg +1");

            // Bare key, no collision -> stays bare.
            assertEquals("000", LoreService.validateLore("000", "", e, saved, game));

            // Explicit tag -> always respected.
            assertEquals("000#mytag", LoreService.validateLore("000#mytag", "", e, saved, game));

            // Tag with characters outside letters/digits -> rejected.
            assertNull(LoreService.validateLore("000#bad tag", "", e, saved, game));

            // Bare key colliding with a different entry -> auto-tag (default trigger is CONTROLLED).
            saved.put("000", entry());
            assertEquals("000#Controlled", LoreService.validateLore("000", "", e, saved, game));

            // Same bare key but it's the entry being edited -> in-place edit, no tag.
            assertEquals("000", LoreService.validateLore("000", "000", e, saved, game));
        }
    }

    // -----------------------------------------------------------------------
    // 25. Conditional per-line gates: trailing ?color / ?faction: / ?round:
    //     tokens, ANDed, evaluated against the player RECEIVING the effect.
    // -----------------------------------------------------------------------

    @Nested
    class ConditionalEffects {

        @Test
        void colorConditionsGateTheLine() {
            // player is red
            LoreEffects.applyLoreEffectsForTest(player, game, entry("!tg +2 ?red"), systemTile, Constants.SPACE, true);
            assertEquals(7, player.getTg());

            LoreEffects.applyLoreEffectsForTest(player, game, entry("!tg +2 ?blue"), systemTile, Constants.SPACE, true);
            assertEquals(7, player.getTg(), "?blue must skip for a red player");

            LoreEffects.applyLoreEffectsForTest(player, game, entry("!tg +2 ?!red"), systemTile, Constants.SPACE, true);
            assertEquals(7, player.getTg(), "?!red must skip for a red player");

            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!tg +2 ?!blue"), systemTile, Constants.SPACE, true);
            assertEquals(9, player.getTg(), "?!blue must fire for a red player");
        }

        @Test
        void factionConditionMatchesTheReceivingPlayersFaction() {
            // player is winnu
            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!tg +2 ?faction:winnu"), systemTile, Constants.SPACE, true);
            assertEquals(7, player.getTg());

            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!tg +2 ?faction:sol"), systemTile, Constants.SPACE, true);
            assertEquals(7, player.getTg());
        }

        @Test
        void roundConditionUsesTheCurrentGameRound() {
            game.setRound(3);

            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!tg +2 ?round:3-6"), systemTile, Constants.SPACE, true);
            assertEquals(7, player.getTg());

            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!tg +2 ?round:4-6"), systemTile, Constants.SPACE, true);
            assertEquals(7, player.getTg());

            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!tg +2 ?round:-2"), systemTile, Constants.SPACE, true);
            assertEquals(7, player.getTg());

            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!tg +2 ?round:3"), systemTile, Constants.SPACE, true);
            assertEquals(9, player.getTg());
        }

        @Test
        void multipleConditionsAndTogether() {
            game.setRound(3);
            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!tg +2 ?red ?round:5-"), systemTile, Constants.SPACE, true);
            assertEquals(5, player.getTg(), "round 3 fails ?round:5- even though ?red holds");

            game.setRound(5);
            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!tg +2 ?red ?round:5-"), systemTile, Constants.SPACE, true);
            assertEquals(7, player.getTg());
        }

        @Test
        void allReceiverEvaluatesConditionsPerRecipient() {
            Player bluePlayer = addRealPlayer("other-user-id", "jolnar", "blue");
            bluePlayer.setTg(0);

            LoreEntry redOnly = entry("!tg +2 ?red");
            redOnly.target = "001";
            redOnly.receiver = LoreService.RECEIVER.ALL;
            LoreService.deliverLore(player, game, "001", true, redOnly, "001");
            assertEquals(7, player.getTg(), "red triggering player matches ?red");
            assertEquals(0, bluePlayer.getTg(), "blue recipient must not match ?red");

            LoreEntry blueOnly = entry("!tg +2 ?blue");
            blueOnly.target = "001";
            blueOnly.receiver = LoreService.RECEIVER.ALL;
            LoreService.deliverLore(player, game, "001", true, blueOnly, "001");
            assertEquals(7, player.getTg(), "red triggering player must not match ?blue");
            assertEquals(2, bluePlayer.getTg(), "blue recipient matches ?blue via the fan-out");
        }

        @Test
        void conditionSyntaxIsValidatedAtSaveTime() {
            assertTrue(LoreEffects.validateEffects(entry("!tg +2 ?red"), game).isEmpty());
            assertTrue(LoreEffects.validateEffects(entry("!tg +2 ?!blue"), game).isEmpty());
            assertTrue(LoreEffects.validateEffects(entry("!tg +2 ?faction:winnu"), game)
                    .isEmpty());
            assertTrue(LoreEffects.validateEffects(entry("!tg +2 ?round:3-6"), game)
                    .isEmpty());
            assertTrue(LoreEffects.validateEffects(entry("!choice", "accept: !tg +2 ?red"), game)
                    .isEmpty());

            assertFalse(
                    LoreEffects.validateEffects(entry("!tg +2 ?xyzzy"), game).isEmpty());
            assertFalse(LoreEffects.validateEffects(entry("!tg +2 ?faction:zzznotafaction"), game)
                    .isEmpty());
            assertFalse(LoreEffects.validateEffects(entry("!tg +2 ?round:6-3"), game)
                    .isEmpty());
            assertFalse(LoreEffects.validateEffects(entry("!tg +2 ?round:abc"), game)
                    .isEmpty());
        }
    }

    // -----------------------------------------------------------------------
    // 26. Validation matrix for the new verbs — written via their short aliases
    //     so alias resolution is covered by the same assertions.
    // -----------------------------------------------------------------------

    @Nested
    class NewVerbValidation {

        private static final String SYMMETRIC_MATRIX =
                "0,0,0,1,0,0;0,0,0,0,0,0;0,0,0,0,0,0;1,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0";

        @Test
        void newVerbsValidateCleanViaAliases() {
            game.setFowMode(true); // fog verbs warn outside FoW
            assertTrue(LoreEffects.validateEffects(entry("!tech gd"), game).isEmpty());
            assertTrue(LoreEffects.validateEffects(entry("!tech random blue"), game)
                    .isEmpty());
            assertTrue(LoreEffects.validateEffects(entry("!rtec gd"), game).isEmpty());
            assertTrue(
                    LoreEffects.validateEffects(entry("!afog 19 Decoy"), game).isEmpty());
            assertTrue(LoreEffects.validateEffects(entry("!rfog"), game).isEmpty());
            assertTrue(LoreEffects.validateEffects(entry("!stl 000 19"), game).isEmpty());
            assertTrue(LoreEffects.validateEffects(entry("!stl 000 random red wormhole"), game)
                    .isEmpty());
            assertTrue(LoreEffects.validateEffects(entry("!rhl 000 2"), game).isEmpty());
            assertTrue(LoreEffects.validateEffects(entry("!rhl 000 -1"), game).isEmpty());
            assertTrue(LoreEffects.validateEffects(
                            entry("!shl 000 " + CustomHyperlaneService.encodeMatrix(SYMMETRIC_MATRIX)), game)
                    .isEmpty());
            assertTrue(LoreEffects.validateEffects(entry("!rcc red"), game).isEmpty());
            assertTrue(LoreEffects.validateEffects(entry("!clru neutral"), game).isEmpty());
        }

        @Test
        void newVerbBadArgsAreFlagged() {
            assertFalse(LoreEffects.validateEffects(entry("!tech"), game).isEmpty());
            assertFalse(LoreEffects.validateEffects(entry("!tech zzznotatech"), game)
                    .isEmpty());
            assertFalse(LoreEffects.validateEffects(entry("!tech random pink"), game)
                    .isEmpty());
            assertFalse(LoreEffects.validateEffects(entry("!removetech zzznotatech"), game)
                    .isEmpty());
            assertFalse(LoreEffects.validateEffects(entry("!settile 000"), game).isEmpty());
            assertFalse(
                    LoreEffects.validateEffects(entry("!settile zzz9 19"), game).isEmpty());
            assertFalse(LoreEffects.validateEffects(entry("!settile 000 zzznotatile"), game)
                    .isEmpty());
            assertFalse(LoreEffects.validateEffects(entry("!settile 000 random purple"), game)
                    .isEmpty());
            assertFalse(
                    LoreEffects.validateEffects(entry("!rotatehyperlane"), game).isEmpty());
            assertFalse(LoreEffects.validateEffects(entry("!rotatehyperlane zzz9"), game)
                    .isEmpty());
            assertFalse(LoreEffects.validateEffects(entry("!rotatehyperlane 000 fast"), game)
                    .isEmpty());
            assertFalse(LoreEffects.validateEffects(entry("!sethyperlane 000 nothex9!"), game)
                    .isEmpty());
        }

        @Test
        void fogVerbsWarnOutsideFowGames() {
            assertFalse(game.isFowMode());
            assertFalse(
                    LoreEffects.validateEffects(entry("!addfogtile 19"), game).isEmpty());
            assertFalse(
                    LoreEffects.validateEffects(entry("!removefogtile"), game).isEmpty());
        }
    }

    // -----------------------------------------------------------------------
    // 27. Tech effects
    // -----------------------------------------------------------------------

    @Nested
    class TechEffects {

        @Test
        void grantIsUnexhaustedAndIdempotent() {
            LoreEffects.applyLoreEffectsForTest(player, game, entry("!tech gd"), systemTile, Constants.SPACE, true);
            assertTrue(player.getTechs().contains("gd"));
            assertFalse(player.getExhaustedTechs().contains("gd"), "a granted tech must arrive readied");

            LoreEffects.applyLoreEffectsForTest(player, game, entry("!tech gd"), systemTile, Constants.SPACE, true);
            assertEquals(
                    1L,
                    player.getTechs().stream().filter("gd"::equals).count(),
                    "re-granting an owned tech must not duplicate it");
        }

        @Test
        void removeTechViaAliasRemovesAnOwnedTech() {
            player.addTech("gd");
            LoreEffects.applyLoreEffectsForTest(player, game, entry("!rtec gd"), systemTile, Constants.SPACE, true);
            assertFalse(player.getTechs().contains("gd"));
        }

        @Test
        void randomBlueGrantsAnUnownedPropulsionTech() {
            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!tech random blue"), systemTile, Constants.SPACE, true);
            assertEquals(1, player.getTechs().size());
            String granted = player.getTechs().get(0);
            assertTrue(Mapper.getTech(granted).isType("propulsion"), "expected a propulsion tech, got: " + granted);
        }

        @Test
        void chooseModeOffersButtonsRatherThanGrantingDirectly() {
            // "choose" sends the player free-research buttons; the tech is only added when they click one
            // (handled by the existing getTech_ handler), so no tech is granted synchronously here.
            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!tech choose blue"), systemTile, Constants.SPACE, true);
            assertTrue(player.getTechs().isEmpty(), "choose must not grant a tech directly");
        }

        @Test
        void chooseAndPickValidateCleanlyWithAndWithoutAType() {
            assertTrue(LoreEffects.validateEffects(entry("!tech choose"), game).isEmpty());
            assertTrue(LoreEffects.validateEffects(entry("!tech choose blue"), game)
                    .isEmpty());
            assertTrue(
                    LoreEffects.validateEffects(entry("!tech pick green"), game).isEmpty());
        }

        @Test
        void chooseWithUnknownTypeIsFlagged() {
            assertFalse(LoreEffects.validateEffects(entry("!tech choose purple"), game)
                    .isEmpty());
        }
    }

    // -----------------------------------------------------------------------
    // 28. Fog tile effects: per-player fowSeenTiles disguise/wipe, FoW only
    // -----------------------------------------------------------------------

    @Nested
    class FogTileEffects {

        @Test
        void addAndRemoveMutateThePlayersFogMap() {
            game.setFowMode(true);

            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!addfogtile 19 Spooky Ruins"), systemTile, Constants.SPACE, true);
            assertEquals("19", player.getFogTiles().get("001"));
            assertEquals("Spooky Ruins", player.getFogLabels().get("001"));

            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!removefogtile"), systemTile, Constants.SPACE, true);
            assertFalse(player.getFogTiles().containsKey("001"));
            assertFalse(player.getFogLabels().containsKey("001"));
        }

        @Test
        void fogEffectsAreNoOpsOutsideFow() {
            assertFalse(game.isFowMode());
            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!addfogtile 19"), systemTile, Constants.SPACE, true);
            assertTrue(player.getFogTiles().isEmpty());
        }
    }

    // -----------------------------------------------------------------------
    // 29. Command token + clearunits effects
    // -----------------------------------------------------------------------

    @Nested
    class CommandTokenAndClearUnitsEffects {

        @Test
        void ccDefaultsToTheTriggeringPlayersColor() {
            LoreEffects.applyLoreEffectsForTest(player, game, entry("!cc"), systemTile, Constants.SPACE, true);
            assertTrue(systemTile.hasCC(Mapper.getCCID("red")));
        }

        @Test
        void ccNeutralResolvesToTheNeutralPlayersColor() {
            LoreEffects.applyLoreEffectsForTest(player, game, entry("!cc neutral"), systemTile, Constants.SPACE, true);
            assertTrue(systemTile.hasCC(Mapper.getCCID("gray")));
        }

        @Test
        void removeccRemovesTheToken() {
            systemTile.addCC(Mapper.getCCID("red"));
            LoreEffects.applyLoreEffectsForTest(player, game, entry("!removecc"), systemTile, Constants.SPACE, true);
            assertFalse(systemTile.hasCC(Mapper.getCCID("red")));
        }

        @Test
        void clearunitsRemovesOnlyTheGivenColorAndKeepsTheHolder() {
            UnitKey redFighter = Units.getUnitKey(UnitType.Fighter, "red");
            UnitKey grayFighter = Units.getUnitKey(UnitType.Fighter, "gray");
            systemTile.addUnit(Constants.SPACE, redFighter, 2);
            systemTile.addUnit(Constants.SPACE, grayFighter, 3);

            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!clearunits neutral"), systemTile, Constants.SPACE, true);

            assertNotNull(systemTile.getUnitHolders().get(Constants.SPACE));
            assertEquals(0, systemTile.getUnitHolders().get(Constants.SPACE).getUnitCount(grayFighter));
            assertEquals(
                    2,
                    systemTile.getUnitHolders().get(Constants.SPACE).getUnitCount(redFighter),
                    "other colors must be untouched");
        }
    }

    // -----------------------------------------------------------------------
    // 30. settile: specific + random placement, and the replace-cleanup path
    // -----------------------------------------------------------------------

    @Nested
    class SetTileEffects {

        @Test
        void placesASpecificTileAtAValidPosition() {
            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!settile 000 19"), systemTile, Constants.SPACE, true);
            assertNotNull(game.getTileByPosition("000"));
            assertEquals("19", game.getTileByPosition("000").getTileID());
        }

        @Test
        void replacingAHyperlanePositionClearsItsCustomData() {
            game.setTile(new Tile("hl", "000"));
            CustomHyperlaneService.insertData(
                    game, "000", "0,0,0,1,0,0;0,0,0,0,0,0;0,0,0,0,0,0;1,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0");

            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!settile 000 19"), systemTile, Constants.SPACE, true);

            assertFalse(
                    game.getCustomHyperlaneData().containsKey("000"),
                    "stale hyperlane data must not survive the tile being replaced");
        }

        @Test
        void randomRedPlacesAnUnusedRedBackTile() {
            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!settile 000 random red"), systemTile, Constants.SPACE, true);
            Tile placed = game.getTileByPosition("000");
            assertNotNull(placed);
            assertEquals(TileModel.TileBack.RED, placed.getTileModel().getTileBack());
        }

        @Test
        void invalidPositionIsANoOp() {
            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!settile zzz9 19"), systemTile, Constants.SPACE, true);
            assertNull(game.getTileByPosition("zzz9"));
        }
    }

    // -----------------------------------------------------------------------
    // 31. Hyperlane effects: rotation direction/identity and the encoded-matrix
    //     form of sethyperlane (raw ';' matrices can't survive footer cleaning)
    // -----------------------------------------------------------------------

    @Nested
    class HyperlaneEffects {

        // Symmetric (0<->3) so insertData's both-ways normalization can't alter it.
        private static final String SIDES_0_3 =
                "0,0,0,1,0,0;0,0,0,0,0,0;0,0,0,0,0,0;1,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0";
        // SIDES_0_3 rotated once: connections move to 1<->4.
        private static final String SIDES_1_4 =
                "0,0,0,0,0,0;0,0,0,0,1,0;0,0,0,0,0,0;0,0,0,0,0,0;0,1,0,0,0,0;0,0,0,0,0,0";
        // A different symmetric matrix (2<->5) for the sethyperlane test.
        private static final String SIDES_2_5 =
                "0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,1;0,0,0,0,0,0;0,0,0,0,0,0;0,0,1,0,0,0";

        @BeforeEach
        void placeCustomHyperlane() {
            game.setTile(new Tile("hl", "000"));
            CustomHyperlaneService.insertData(game, "000", SIDES_0_3);
        }

        private String storedEncoded() {
            return CustomHyperlaneService.encodeMatrix(
                    game.getCustomHyperlaneData().get("000"));
        }

        @Test
        void rotateOnceMovesConnectionsOneSideClockwise() {
            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!rotatehyperlane 000"), systemTile, Constants.SPACE, true);
            assertEquals(CustomHyperlaneService.encodeMatrix(SIDES_1_4), storedEncoded());
        }

        @Test
        void sixStepsIsAFullTurnNoOp() {
            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!rotatehyperlane 000 6"), systemTile, Constants.SPACE, true);
            assertEquals(CustomHyperlaneService.encodeMatrix(SIDES_0_3), storedEncoded());
        }

        @Test
        void negativeStepsRotateBackwards() {
            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!rotatehyperlane 000 -1"), systemTile, Constants.SPACE, true);
            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!rotatehyperlane 000 1"), systemTile, Constants.SPACE, true);
            assertEquals(CustomHyperlaneService.encodeMatrix(SIDES_0_3), storedEncoded(), "-1 then +1 must cancel out");
        }

        @Test
        void sethyperlaneAppliesAnEncodedMatrix() {
            String encoded = CustomHyperlaneService.encodeMatrix(SIDES_2_5);
            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!sethyperlane 000 " + encoded), systemTile, Constants.SPACE, true);
            assertEquals(encoded, storedEncoded());
        }

        @Test
        void refusesToTouchANonHyperlaneTile() {
            LoreEffects.applyLoreEffectsForTest(
                    player, game, entry("!rotatehyperlane 001 1"), systemTile, Constants.SPACE, true);
            assertFalse(game.getCustomHyperlaneData().containsKey("001"));
            assertEquals(
                    CustomHyperlaneService.encodeMatrix(SIDES_0_3),
                    storedEncoded(),
                    "the real hyperlane at 000 must be untouched");
        }
    }
}
