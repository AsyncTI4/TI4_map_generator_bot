package ti4.ai.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.eval.MoveEnablers.Enabler;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelperTacticalAction;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Nav Suite: "During the Movement step of this tactical action, ignore the effect of anomalies" (rulings in
// docs/rules/cards/action-cards.md, Nav Suite I-VII). Each case checks a cruiser's range (move 2) with and without
// the card, for the playing seat only and only during this activation's movement.
class NavSuiteMovementTest extends BaseTi4Test {

    private static <T> T nav(Player seat, Supplier<T> evaluation) {
        return MoveEnablers.assuming(seat, Enabler.NAV_SUITE, evaluation);
    }

    private static boolean reaches(MoveLine line, int origin, int step) {
        Tile target = line.at(step);
        return MoveBudget.feasible(
                line.test.game,
                line.test.nekro,
                target,
                List.of(new Departure(line.positions.get(origin), UnitType.Cruiser, 1)));
    }

    private static boolean reachesWithNavSuite(MoveLine line, int origin, int step) {
        return nav(line.test.nekro, () -> reaches(line, origin, step));
    }

    // Nav Suite V: ships may move through a nebula, which otherwise stops movement.
    @Test
    void passesThroughANebula() {
        MoveLine line = new MoveLine(2);
        line.swap(1, "42");
        line.ships(0, UnitType.Cruiser, 1);

        assertThat(reaches(line, 0, 1)).isTrue();
        assertThat(reaches(line, 0, 2)).isFalse();
        assertThat(reachesWithNavSuite(line, 0, 2)).isTrue();
    }

    // Nav Suite V: ships moving out of a nebula keep their normal move value instead of move 1.
    @Test
    void leavesANebulaWithItsFullMove() {
        MoveLine line = new MoveLine(3);
        line.swap(1, "42");
        line.ships(1, UnitType.Cruiser, 1);

        assertThat(reaches(line, 1, 2)).isTrue();
        assertThat(reaches(line, 1, 3)).isFalse();
        assertThat(reachesWithNavSuite(line, 1, 3)).isTrue();
    }

    // Nav Suite III: ships may move into and through an asteroid field without Antimass Deflectors.
    @Test
    void entersAndCrossesAnAsteroidField() {
        MoveLine line = new MoveLine(2);
        line.swap(1, "44");
        line.ships(0, UnitType.Cruiser, 1);

        assertThat(reaches(line, 0, 1)).isFalse();
        assertThat(reaches(line, 0, 2)).isFalse();
        assertThat(reachesWithNavSuite(line, 0, 1)).isTrue();
        assertThat(reachesWithNavSuite(line, 0, 2)).isTrue();
    }

    // Nav Suite VI: ships may move into and through a supernova.
    @Test
    void entersAndCrossesASupernova() {
        MoveLine line = new MoveLine(2);
        line.swap(1, "43");
        line.ships(0, UnitType.Cruiser, 1);

        assertThat(reaches(line, 0, 1)).isFalse();
        assertThat(reaches(line, 0, 2)).isFalse();
        assertThat(reachesWithNavSuite(line, 0, 1)).isTrue();
        assertThat(reachesWithNavSuite(line, 0, 2)).isTrue();
    }

    // Ships that ended a Nav Suite move in an asteroid field or supernova may leave it later without the card (III,
    // VI).
    @Test
    void shipsLeaveAnAsteroidFieldOrSupernovaWithoutTheCard() {
        MoveLine line = new MoveLine(3);
        line.swap(1, "44");
        line.swap(2, "43");
        line.ships(1, UnitType.Cruiser, 1);
        line.ships(2, UnitType.Cruiser, 1);

        assertThat(reaches(line, 2, 3)).isTrue();
        assertThat(reaches(line, 1, 3)).isFalse();
    }

    // The engine stores the played card for the active seat; once movement is finished (or another seat is active)
    // the next activation feels the anomalies again.
    @Test
    void aPlayedNavSuiteEndsWithThisActivation() {
        MoveLine line = new MoveLine(1);
        line.swap(1, "44");
        line.ships(0, UnitType.Cruiser, 1);
        AiTestGame test = line.test;
        test.aiIsActive("action");
        test.game.setActiveSystem(line.positions.get(1));
        test.game.setStoredValue(
                "currentActionSummary" + test.nekro.getFaction(), " Activated " + line.positions.get(1));
        test.game.setStoredValue(MoveEnablers.NAV_SUITE_PLAYED, test.nekro.getFaction());
        assertThat(reaches(line, 0, 1)).isTrue();

        test.isActive(test.sol, "action");
        assertThat(reaches(line, 0, 1)).isFalse();

        test.aiIsActive("action");
        assertThat(reaches(line, 0, 1)).isTrue();
        ButtonHelperTacticalAction.clearMovementEnablers(test.game);
        assertThat(reaches(line, 0, 1)).isFalse();
    }

    // Nav Suite covers only this tactical action's movement: a later round's reach still stops at the asteroid field,
    // even while the card is assumed for the move being planned.
    @Test
    void laterLegsStillFeelTheAnomalies() {
        MoveLine line = new MoveLine(1);
        line.swap(1, "44");
        line.ships(0, UnitType.Cruiser, 1);
        AiTestGame test = line.test;
        test.nekro.setTacticalCC(3);
        Tile asteroids = line.at(1);

        Map<String, Map<UnitType, Integer>> now = nav(test.nekro, () -> within(test, asteroids, Journey.thisAction()));
        Map<String, Map<UnitType, Integer>> later = nav(test.nekro, () -> within(test, asteroids, Journey.nextRound()));

        assertThat(now).containsKey(AiTestGame.HOME);
        assertThat(later).isEmpty();
        assertThat(nav(
                        test.nekro,
                        () -> MoveEnablers.laterActivation(
                                () -> MovementGraph.canEnter(test.game, test.nekro, asteroids))))
                .isFalse();
    }

    // Nav Suite VII: only the player who played it ignores anomalies. An enemy is never assumed to hold it, and an
    // enemy who did play it ignores anomalies only during its own activation.
    @Test
    void anEnemyIgnoresAnomaliesOnlyAfterPlayingNavSuite() {
        MoveLine line = new MoveLine(1);
        Tile supernova = line.swap(1, "43");
        AiTestGame test = line.test;
        test.sol.setActionCard("nav_suite", 403);
        Supplier<Boolean> solEnters = () -> MovementGraph.canEnter(test.game, test.sol, supernova);

        assertThat(DecidingSeat.of(test.nekro, solEnters)).isFalse();
        assertThat(nav(test.nekro, solEnters)).isFalse();

        test.game.setStoredValue(MoveEnablers.NAV_SUITE_PLAYED, test.sol.getFaction());
        assertThat(DecidingSeat.of(test.nekro, solEnters)).isFalse();

        test.isActive(test.sol, "action");
        test.game.setStoredValue(
                "currentActionSummary" + test.sol.getFaction(), " Activated " + supernova.getPosition());
        assertThat(DecidingSeat.of(test.nekro, solEnters)).isTrue();
        assertThat(MovementGraph.canEnter(test.game, test.nekro, supernova)).isFalse();
    }

    private static Map<String, Map<UnitType, Integer>> within(AiTestGame test, Tile target, Journey journey) {
        return MoveBudget.shipsWithin(
                test.game, test.nekro, target, journey, origin -> true, type -> 1, FleetLimit.ships(test.nekro));
    }
}
