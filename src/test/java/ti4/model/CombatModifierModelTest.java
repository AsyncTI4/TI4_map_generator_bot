package ti4.model;

import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.combat.CombatRollType;
import ti4.testUtils.BaseTi4Test;

class CombatModifierModelTest extends BaseTi4Test {

    @Test
    void bestScopeSkipsUnitsThatRollNoDice() {
        CombatModifierModel tnelisPenalty = Mapper.getCombatModifiers().get("minus1_roll_tnelis_flagship");
        UnitModel noDiceShip = Mapper.getUnit("pbd2000_flagship");
        UnitModel destroyer = Mapper.getUnit("destroyer");
        Assertions.assertEquals(0, noDiceShip.getCombatDieCount());
        Assertions.assertTrue(
                noDiceShip.getCombatHitsOn() < destroyer.getCombatHitsOn(),
                "the no-dice ship must out-sort the destroyer on hit threshold for this scenario");
        List<UnitModel> units = List.of(noDiceShip, destroyer);
        Game game = new Game();
        Player player = new Player("1", "test", game);

        Assertions.assertFalse(
                tnelisPenalty.isInScopeForUnit(noDiceShip, units, CombatRollType.combatround, game, player));
        Assertions.assertTrue(
                tnelisPenalty.isInScopeForUnit(destroyer, units, CombatRollType.combatround, game, player));
    }

    @Test
    void bestScopeStillPicksAUnitWhenNoneRollDice() {
        CombatModifierModel tnelisPenalty = Mapper.getCombatModifiers().get("minus1_roll_tnelis_flagship");
        UnitModel noDiceShip = Mapper.getUnit("pbd2000_flagship");
        Game game = new Game();
        Player player = new Player("1", "test", game);

        Assertions.assertTrue(tnelisPenalty.isInScopeForUnit(
                noDiceShip, List.of(noDiceShip), CombatRollType.combatround, game, player));
    }
}
