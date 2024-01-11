package ti4.helpers;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.ArrayList;
import java.util.List;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.Player;
import ti4.model.CombatModifierModel;
import ti4.model.UnitModel;
import ti4.testUtils.BaseTi4Test;

public class CombatModHelperTest extends BaseTi4Test {

    @Test
    public void testNonFighters() {
        CombatModifierModel model = new CombatModifierModel();
        model.setValue(1);
        model.setValueScalingType(Constants.MOD_OPPONENT_NON_FIGHTER_SHIP);

        Player player = new Player();
        Player opponent = new Player();
        Game game = new Game();

        UnitModel nonFighter = new UnitModel();
        nonFighter.setBaseType(UnitType.Carrier.value);

        List<UnitModel> opponentUnits = new ArrayList<>();
        opponentUnits.add(nonFighter);
        opponentUnits.add(nonFighter);

        assertEquals(2, CombatModHelper.GetVariableModValue(model, player, opponent, game, opponentUnits, nonFighter));
    }

    @Test
    public void testNonFightersExcludesFighters() {
        CombatModifierModel model = new CombatModifierModel();
        model.setValue(1);
        model.setValueScalingType(Constants.MOD_OPPONENT_NON_FIGHTER_SHIP);

        Player player = new Player();
        Player opponent = new Player();
        Game game = new Game();

        UnitModel nonFighter = new UnitModel();
        nonFighter.setBaseType(UnitType.Carrier.value);

        UnitModel fighter = new UnitModel();
        fighter.setBaseType(UnitType.Fighter.value);

        List<UnitModel> opponentUnits = new ArrayList<>();
        opponentUnits.add(nonFighter);
        opponentUnits.add(fighter);

        assertEquals(1, CombatModHelper.GetVariableModValue(model, player, opponent, game, opponentUnits, fighter));
    }
}
