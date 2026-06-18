package ti4.service.fow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.service.fow.LoreService.LoreEntry;
import ti4.testUtils.BaseTi4Test;

class LoreServiceTest extends BaseTi4Test {

    private Game game;
    private Player player;
    private Tile systemTile; // tile 18 = Mecatol Rex, position "001"
    private Tile remoteTile; // tile 19, position "002" — for @location override tests

    @BeforeEach
    void setUp() {
        game = new Game();

        player = game.addPlayer("test-user-id", "winnu");
        player.setColor("red");
        player.setTacticalCC(2);
        player.setFleetCC(3);
        player.setStrategicCC(2);
        player.setTg(5);
        player.setCommoditiesBase(4);
        game.setActionCards(Mapper.getShuffledDeck("action_cards_pok"));

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
}
