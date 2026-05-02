package ti4.contest.replay.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.contest.replay.core.CombatReplayDecoys.Decoy;
import ti4.contest.replay.core.CombatReplayDecoys.DecoyUnit;
import ti4.contest.replay.core.CombatRollPayload.DieRollSource;
import ti4.contest.replay.core.CombatRollPayload.RollSegmentType;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.service.combat.CombatRollType;
import ti4.testUtils.BaseTi4Test;

class CombatReplayDecoysTest extends BaseTi4Test {

    @Test
    void globalDecoysFlagDefaultsOff() {
        assertFalse(new CombatContestSettings().isDecoysEnabled());
    }

    @Test
    void addsDecoyUnitsToRenderedTileOnly() {
        Game game = new Game();
        Tile tile = new Tile("19", "000");
        game.setTile(tile);
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Cruiser, "tqs"), 2);
        CombatReplayDecoys.Abilities abilities =
                abilities(new DecoyUnit("ghost", "<ghost>", "tqs", UnitType.Cruiser, "space", 1));

        CombatReplayDecoys.applyToTile(game, "000", abilities);

        assertEquals(3, tile.getSpaceUnitHolder().getUnitCount(Units.getUnitKey(UnitType.Cruiser, "tqs")));
    }

    @Test
    void warSunDecoyGrantsReplayOnlyWarSunTechForRenderedTile() {
        Game game = new Game();
        Tile tile = new Tile("19", "000");
        game.setTile(tile);
        Player letnev = player(game, "letnev", "blue");
        game.getPlayers().put(letnev.getUserID(), letnev);
        CombatReplayDecoys.Abilities abilities =
                abilities(new DecoyUnit("letnev", "<letnev>", "blu", UnitType.Warsun, "space", 1));

        CombatReplayDecoys.applyToTile(game, "000", abilities);

        assertTrue(letnev.hasWarsunTech());
        assertTrue(letnev.hasTech("ws"));
        assertEquals(1, tile.getSpaceUnitHolder().getUnitCount(Units.getUnitKey(UnitType.Warsun, "blu")));
    }

    @Test
    void warSunDecoyWithFullColorNameRendersOnTile() {
        Game game = new Game();
        Tile tile = new Tile("19", "000");
        game.setTile(tile);
        Player yin = player(game, "yin", "sunset");
        game.getPlayers().put(yin.getUserID(), yin);
        CombatReplayDecoys.Abilities abilities =
                abilities(new DecoyUnit("yin", "<yin>", "sunset", UnitType.Warsun, "space", 1));

        CombatReplayDecoys.applyToTile(game, "000", abilities);

        assertTrue(yin.hasWarsunTech());
        assertEquals(1, tile.getSpaceUnitHolder().getUnitCount(Units.getUnitKey(UnitType.Warsun, yin.getColorID())));
    }

    @Test
    void addsForcedMissDiceToMatchingRollWithoutChangingTotals() {
        CombatRollPayload payload = new CombatRollPayload(
                new CombatRollPayload.RollHeader(
                        "ghost",
                        "turquoise",
                        "<ghost>",
                        "yin",
                        "sunset",
                        "000",
                        "18",
                        "space",
                        "space combat",
                        CombatRollType.combatround,
                        1,
                        false,
                        false),
                List.of(),
                List.of(),
                List.of(new CombatRollPayload.UnitRoll(
                        "ghost_cruiser2",
                        "ca",
                        "cruiser",
                        "Cruiser II",
                        "Cruiser II",
                        "<cruiser>",
                        2,
                        1,
                        0,
                        6,
                        2,
                        4,
                        RollSegmentType.PRIMARY,
                        List.of(
                                new CombatRollPayload.DieRoll(8, 4, true, DieRollSource.PRIMARY),
                                new CombatRollPayload.DieRoll(2, 4, false, DieRollSource.PRIMARY)),
                        1)),
                new CombatRollPayload.RollTotal(2, 1, 1, 2));
        CombatReplayDecoys.Abilities abilities =
                abilities(new DecoyUnit("ghost", "<ghost>", "tqs", UnitType.Cruiser, "space", 1));

        CombatRollPayload transformed = CombatReplayDecoys.applyToRoll(payload, abilities);
        CombatRollPayload.UnitRoll unitRoll = transformed.unitRolls().getFirst();
        CombatRollPayload.DieRoll decoyDie = unitRoll.dice().getLast();

        assertEquals(3, unitRoll.quantity());
        assertEquals(3, unitRoll.dice().size());
        assertEquals(1, unitRoll.hits());
        assertSame(payload.total(), transformed.total());
        assertEquals(DieRollSource.DECOY, decoyDie.source());
        assertFalse(decoyDie.success());
        assertTrue(decoyDie.result() < 4);
    }

    @Test
    void addsSyntheticForcedMissRollWhenDecoyUnitHasNoOriginalRoll() {
        CombatRollPayload.RollTotal total = new CombatRollPayload.RollTotal(1, 0, 1, 1);
        CombatRollPayload payload = new CombatRollPayload(
                new CombatRollPayload.RollHeader(
                        "yin",
                        "sunset",
                        "<yin>",
                        "letnev",
                        "blue",
                        "000",
                        "18",
                        "space",
                        "space combat",
                        CombatRollType.combatround,
                        1,
                        false,
                        false),
                List.of(),
                List.of(),
                List.of(new CombatRollPayload.UnitRoll(
                        "yin_destroyer",
                        "dd",
                        "destroyer",
                        "Destroyer I",
                        "Destroyer I",
                        "<destroyer>",
                        1,
                        1,
                        0,
                        9,
                        0,
                        9,
                        RollSegmentType.PRIMARY,
                        List.of(new CombatRollPayload.DieRoll(2, 9, false, DieRollSource.PRIMARY)),
                        0)),
                total);
        CombatReplayDecoys.Abilities abilities =
                abilities(new DecoyUnit("yin", "<yin>", "sunset", UnitType.Warsun, "space", 1));

        CombatRollPayload transformed = CombatReplayDecoys.applyToRoll(payload, abilities);
        CombatRollPayload.UnitRoll warSunRoll = transformed.unitRolls().get(1);

        assertEquals(2, transformed.unitRolls().size());
        assertSame(total, transformed.total());
        assertEquals("ws", warSunRoll.asyncId());
        assertEquals("warsun", warSunRoll.baseType());
        assertEquals(1, warSunRoll.quantity());
        assertEquals(3, warSunRoll.dice().size());
        assertEquals(0, warSunRoll.hits());
        assertTrue(warSunRoll.dice().stream().allMatch(die -> die.source() == DieRollSource.DECOY));
        assertTrue(warSunRoll.dice().stream().noneMatch(CombatRollPayload.DieRoll::success));
    }

    @Test
    void addsForcedMissDiceToMatchingRerolls() {
        CombatRollPayload payload = new CombatRollPayload(
                new CombatRollPayload.RollHeader(
                        "ghost",
                        "turquoise",
                        "<ghost>",
                        "yin",
                        "sunset",
                        "000",
                        "18",
                        "space",
                        "space combat",
                        CombatRollType.combatround,
                        1,
                        false,
                        false),
                List.of(),
                List.of(),
                List.of(
                        new CombatRollPayload.UnitRoll(
                                "ghost_destroyer",
                                "dd",
                                "destroyer",
                                "Destroyer I",
                                "Destroyer I",
                                "<destroyer>",
                                1,
                                1,
                                0,
                                9,
                                0,
                                9,
                                RollSegmentType.PRIMARY,
                                List.of(new CombatRollPayload.DieRoll(2, 9, false, DieRollSource.PRIMARY)),
                                0),
                        new CombatRollPayload.UnitRoll(
                                "ghost_destroyer",
                                "dd",
                                "destroyer",
                                "Destroyer I",
                                "Destroyer I",
                                "<destroyer>",
                                1,
                                1,
                                0,
                                9,
                                0,
                                9,
                                RollSegmentType.JOL_NAR_COMMANDER_REROLL_MISSES,
                                List.of(new CombatRollPayload.DieRoll(7, 9, false, DieRollSource.REROLL_MISS)),
                                0)),
                new CombatRollPayload.RollTotal(2, 0, 1, 2));
        CombatReplayDecoys.Abilities abilities =
                abilities(new DecoyUnit("ghost", "<ghost>", "tqs", UnitType.Destroyer, "space", 3));

        CombatRollPayload transformed = CombatReplayDecoys.applyToRoll(payload, abilities);
        CombatRollPayload.UnitRoll reroll = transformed.unitRolls().get(1);

        assertEquals(4, reroll.quantity());
        assertEquals(4, reroll.dice().size());
        assertEquals(
                3,
                reroll.dice().stream()
                        .filter(die -> die.source() == DieRollSource.DECOY)
                        .count());
        assertTrue(reroll.dice().stream()
                .filter(die -> die.source() == DieRollSource.DECOY)
                .noneMatch(CombatRollPayload.DieRoll::success));
    }

    @Test
    void rendersDecoyDisappearanceMessage() {
        CombatReplayDecoys.Abilities abilities = abilities(
                new DecoyUnit("ghost", "<ghost>", "tqs", UnitType.Cruiser, "space", 2),
                new DecoyUnit("yin", "<yin>", "sns", UnitType.Destroyer, "space", 1));

        String message = CombatReplayDecoys.renderDisappearanceMessage(abilities);

        assertTrue(message.contains("## False Colors Revealed"));
        assertTrue(message.contains("<ghost> 2 cruisers"));
        assertTrue(message.contains("<yin> 1 destroyer"));
    }

    @Test
    void doesNotCaptureImplicitDecoysFromRealUnits() {
        Game game = new Game();
        Tile tile = new Tile("19", "000");
        game.setTile(tile);
        Player ghost = player(game, "ghost", "turquoise");
        Player yin = player(game, "yin", "sunset");
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Cruiser, "tqs"), 1);
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Destroyer, "sns"), 1);
        CombatReplayDecoys.clearDebugDecoys(game, tile);

        assertNull(CombatReplayDecoys.buildJson(ghost, yin, tile, true));
    }

    @Test
    void globalFlagSuppressesDebugDecoys() {
        Game game = new Game();
        Tile tile = new Tile("19", "000");
        game.setTile(tile);
        Player ghost = player(game, "ghost", "turquoise");
        Player yin = player(game, "yin", "sunset");
        CombatReplayDecoys.clearDebugDecoys(game, tile);
        CombatReplayDecoys.addDebugDecoyUnit(game, tile, ghost, UnitType.Destroyer);

        assertNull(CombatReplayDecoys.buildJson(ghost, yin, tile, false));
    }

    @Test
    void debugDecoyOverrideCapturesBuiltUnitMix() {
        Game game = new Game();
        Tile tile = new Tile("19", "000");
        game.setTile(tile);
        Player ghost = player(game, "ghost", "turquoise");
        Player yin = player(game, "yin", "sunset");
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Cruiser, "tqs"), 1);
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Destroyer, "sns"), 1);
        CombatReplayDecoys.clearDebugDecoys(game, tile);

        CombatReplayDecoys.addDebugDecoyUnit(game, tile, ghost, UnitType.Destroyer);
        CombatReplayDecoys.addDebugDecoyUnit(game, tile, ghost, UnitType.Destroyer);
        CombatReplayDecoys.addDebugDecoyUnit(game, tile, ghost, UnitType.Fighter);
        CombatReplayDecoys.Abilities abilities =
                CombatReplayDecoys.read(CombatReplayDecoys.buildJson(ghost, yin, tile, true));

        assertTrue(abilities.hasDecoys());
        assertEquals(2, abilities.decoy().units().size());
        assertEquals("ghost", abilities.decoy().units().getFirst().faction());
        assertEquals(UnitType.Destroyer, abilities.decoy().units().getFirst().unitType());
        assertEquals(2, abilities.decoy().units().getFirst().count());
        assertEquals(UnitType.Fighter, abilities.decoy().units().get(1).unitType());
    }

    @Test
    void persistedDecoyChoiceMergesIntoReplayAbilities() {
        String abilitiesJson = CombatReplayDecoys.addDecoy(
                null, new DecoyUnit("mentak", "<mentak>", "blu", UnitType.Destroyer, Constants.SPACE, 1));
        abilitiesJson = CombatReplayDecoys.addDecoy(
                abilitiesJson, new DecoyUnit("mentak", "<mentak>", "blu", UnitType.Destroyer, Constants.SPACE, 1));

        CombatReplayDecoys.Abilities abilities = CombatReplayDecoys.read(abilitiesJson);

        assertTrue(abilities.hasDecoys());
        assertEquals(1, abilities.decoy().units().size());
        assertEquals(UnitType.Destroyer, abilities.decoy().units().getFirst().unitType());
        assertEquals(2, abilities.decoy().units().getFirst().count());
    }

    @Test
    void debugDecoyOverrideCanSuppressDecoys() {
        Game game = new Game();
        Tile tile = new Tile("19", "000");
        game.setTile(tile);
        Player ghost = player(game, "ghost", "turquoise");
        Player yin = player(game, "yin", "sunset");
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Cruiser, "tqs"), 1);
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Destroyer, "sns"), 1);
        CombatReplayDecoys.clearDebugDecoys(game, tile);

        CombatReplayDecoys.setDebugDecoyUnits(game, tile, List.of());

        assertNull(CombatReplayDecoys.buildJson(ghost, yin, tile, true));
    }

    @Test
    void rendersNoDebugDecoysForEmptyOverride() {
        Game game = new Game();
        Tile tile = new Tile("19", "000");
        game.setTile(tile);
        CombatReplayDecoys.clearDebugDecoys(game, tile);

        CombatReplayDecoys.setDebugDecoyUnits(game, tile, List.of());

        assertEquals("No decoys selected.", CombatReplayDecoys.renderDebugDecoySummary(game, tile));
        assertEquals("Space\n", CombatReplayDecoys.appendDebugDecoySummary("Space\n", game, tile));
    }

    @Test
    void appendsDebugDecoysToTileSummary() {
        Game game = new Game();
        Tile tile = new Tile("19", "000");
        game.setTile(tile);
        Player ghost = player(game, "ghost", "turquoise");
        CombatReplayDecoys.clearDebugDecoys(game, tile);

        CombatReplayDecoys.addDebugDecoyUnit(game, tile, ghost, UnitType.Carrier);
        CombatReplayDecoys.addDebugDecoyUnit(game, tile, ghost, UnitType.Carrier);

        String summary = CombatReplayDecoys.appendDebugDecoySummary("Space\n", game, tile);

        assertTrue(summary.contains("Replay-Only Decoys"));
        assertTrue(summary.contains("2x"));
        assertTrue(summary.contains("Carrier"));
        assertTrue(summary.contains("[Decoy]"));
    }

    private CombatReplayDecoys.Abilities abilities(DecoyUnit... units) {
        return new CombatReplayDecoys.Abilities(new Decoy(List.of(units)));
    }

    private Player player(Game game, String faction, String color) {
        Player player = new Player(faction, faction, game);
        player.setFaction(faction);
        player.setColor(color);
        return player;
    }
}
