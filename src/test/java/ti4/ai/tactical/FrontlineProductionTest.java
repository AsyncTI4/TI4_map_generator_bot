package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.tactical.ProductionPlanner.BuildPlan;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

// A dock within two moves of a rival that is worth hurting builds its warships with more enthusiasm than the same dock
// far from every rival.
class FrontlineProductionTest extends BaseTi4Test {

    private AiTestGame test;
    private Tile home;
    private Player sardakk;

    private void board(boolean rivalNextDoor) {
        test = new AiTestGame();
        test.game.setRound(4);
        home = test.nekroHome();
        test.nekro.setFleetCC(5);
        test.nekro.setTg(10);
        test.units(home, "space", test.nekro, UnitType.Carrier, 2);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 5);
        sardakk = test.addSeat("200000000000000002", "sardakk", "red");
        sardakk.addPlanet("trenlak");
        sardakk.addPlanet("quinarra");
        test.game.scorePublicObjective(sardakk.getUserID(), test.game.addCustomPO("Leading", 4));
        String between = AiTestGame.neighbourOf(AiTestGame.HOME);
        test.place("46", between);
        String rivalPosition = rivalNextDoor
                ? MapPositionMapper.getAdjacentTilePositions(between).stream()
                        .filter(position -> !"x".equals(position)
                                && !AiTestGame.HOME.equals(position)
                                && !MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME)
                                        .contains(position))
                        .findFirst()
                        .orElseThrow()
                : DefenseScenario.SARDAKK_HOME_POSITION;
        test.place("13", rivalPosition);
    }

    private BuildPlan plan() {
        return ProductionPlanner.plan(test.game, test.nekro, home);
    }

    // Test 21: the same dock, the same money: with the leader's home two moves away its surplus warships are valued
    // higher than with the leader's home far off.
    @Test
    void aDockNextToARivalValuesItsWarshipsHigher() {
        board(true);
        BuildPlan frontline = plan();
        board(false);
        BuildPlan rear = plan();

        assertThat(frontline.units(UnitType.Flagship)).isEqualTo(rear.units(UnitType.Flagship));
        assertThat(frontline.value()).isGreaterThan(rear.value());
    }

    @Test
    void aDockFarFromEveryRivalGetsNoBonus() {
        board(false);
        double far = plan().value();
        board(false);
        test.game.scorePublicObjective(sardakk.getUserID(), test.game.addCustomPO("More", 3));

        assertThat(plan().value()).isEqualTo(far);
    }
}
