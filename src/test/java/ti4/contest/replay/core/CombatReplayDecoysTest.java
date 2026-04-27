package ti4.contest.replay.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.contest.replay.core.CombatReplayDecoys.Decoy;
import ti4.contest.replay.core.CombatReplayDecoys.DecoyUnit;
import ti4.contest.replay.core.CombatRollPayload.DieRollSource;
import ti4.contest.replay.core.CombatRollPayload.RollSegmentType;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.service.combat.CombatRollType;
import ti4.testUtils.BaseTi4Test;

class CombatReplayDecoysTest extends BaseTi4Test {

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
    void rendersDecoyDisappearanceMessage() {
        CombatReplayDecoys.Abilities abilities = abilities(
                new DecoyUnit("ghost", "<ghost>", "tqs", UnitType.Cruiser, "space", 2),
                new DecoyUnit("yin", "<yin>", "sns", UnitType.Destroyer, "space", 1));

        String message = CombatReplayDecoys.renderDisappearanceMessage(abilities);

        assertTrue(message.contains("## Sensor Echoes Fade"));
        assertTrue(message.contains("<ghost> 2 cruisers"));
        assertTrue(message.contains("<yin> 1 destroyer"));
    }

    private CombatReplayDecoys.Abilities abilities(DecoyUnit... units) {
        return new CombatReplayDecoys.Abilities(new Decoy(List.of(units)));
    }
}
