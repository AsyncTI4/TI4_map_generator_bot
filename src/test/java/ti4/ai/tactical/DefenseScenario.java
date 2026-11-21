package ti4.ai.tactical;

import java.util.function.Function;
import ti4.ai.AiTestGame;
import ti4.ai.eval.BoardView;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;

/**
 * The board most defence tests share: Nekro's home (Mordai II, one planet, a space dock) in round 4 with three tactical
 * tokens, a Sardakk seat whose own home is far away, and an empty system next to Nekro's home where the Sardakk fleet
 * waits.
 */
final class DefenseScenario {

    static final String SARDAKK_ID = "200000000000000002";
    static final String SARDAKK_HOME_POSITION = "310";
    static final String EMPTY_SYSTEM = "46";

    final AiTestGame test = new AiTestGame();
    final Tile home;
    final Player sardakk;
    final String stagingPosition = AiTestGame.neighbourOf(AiTestGame.HOME);
    final Tile staging;

    DefenseScenario() {
        this(AiTestGame::nekroHome);
    }

    /** The same board with another home system for the Nekro seat, built by {@code homeBuilder} at {@link AiTestGame#HOME}. */
    DefenseScenario(Function<AiTestGame, Tile> homeBuilder) {
        test.game.setRound(4);
        test.nekro.setTacticalCC(3);
        test.nekro.setFleetCC(5);
        home = homeBuilder.apply(test);
        sardakk = test.addSeat(SARDAKK_ID, "sardakk", "red");
        sardakk.setFleetCC(5);
        placeSardakkHome();
        staging = test.place(EMPTY_SYSTEM, stagingPosition);
    }

    void placeSardakkHome() {
        test.place("13", SARDAKK_HOME_POSITION);
        sardakk.addPlanet("trenlak");
        sardakk.addPlanet("quinarra");
    }

    void raidingFleet(int carriers, int cruisers, int infantry) {
        test.units(staging, "space", sardakk, UnitType.Carrier, carriers);
        test.units(staging, "space", sardakk, UnitType.Cruiser, cruisers);
        test.units(staging, "space", sardakk, UnitType.Infantry, infantry);
    }

    void reveal(String objective) {
        test.game
                .getRevealedPublicObjectives()
                .put(objective, test.game.getRevealedPublicObjectives().size() + 1);
    }

    String beyondStaging() {
        var nearHome = MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME);
        return MapPositionMapper.getAdjacentTilePositions(stagingPosition).stream()
                .filter(candidate -> !"x".equals(candidate)
                        && !AiTestGame.HOME.equals(candidate)
                        && !nearHome.contains(candidate)
                        && !SARDAKK_HOME_POSITION.equals(candidate))
                .findFirst()
                .orElseThrow();
    }

    String otherNeighbourOfHome() {
        return MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME).stream()
                .filter(candidate -> !"x".equals(candidate) && !stagingPosition.equals(candidate))
                .findFirst()
                .orElseThrow();
    }

    Defenders defenders() {
        return Defenders.of(test.nekro, home);
    }

    Defenders afterBuilding(ProductionPlanner.BuildPlan plan) {
        return afterBuilding(defenders(), plan);
    }

    static Defenders afterBuilding(Defenders defenders, ProductionPlanner.BuildPlan plan) {
        Defenders grown = defenders;
        for (ProductionPlanner.BuildOrder order : plan.orders()) {
            grown = BoardView.GROUND_FORCES.contains(order.type())
                    ? grown.plusGround(order.location(), order.type(), order.units())
                    : grown.plusShips(order.type(), order.units(), 0);
        }
        return grown;
    }
}
