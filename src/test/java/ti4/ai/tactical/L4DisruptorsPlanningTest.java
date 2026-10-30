package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.letnev.L4Disruptors;
import ti4.ai.tactical.IncomingForces.Horizon;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// L4 Disruptors: during an invasion, units cannot use space cannon against the owner's units. Invasion planning and the
// threat evaluation therefore leave the planets' space cannon out of the landing; the cannon still fires at ships.
class L4DisruptorsPlanningTest extends BaseTi4Test {

    private static final String HOME_PLANET = "mordaiii";
    private static final String HOME_POSITION = AiTestGame.HOME;

    private DefenseScenario board;
    private Tile target;

    @BeforeEach
    void setUp() {
        board = new DefenseScenario();
        board.test.nekro.setFleetCC(10);
        target = board.test.place("26", board.otherNeighbourOfHome());
        board.sardakk.addPlanet("lodor");
        board.test.units(target, "lodor", board.sardakk, UnitType.Pds, 1);
    }

    private StrikeEstimate landing() {
        List<UnitMove> moves = List.of(
                new UnitMove(HOME_POSITION, "space", UnitType.Carrier, 2),
                new UnitMove(HOME_POSITION, HOME_PLANET, UnitType.Infantry, 4));
        Strike strike = Strike.of(
                board.test.nekro,
                target,
                board.sardakk,
                List.of(board.home),
                moves,
                new StrikeRules(false, 0.5, true, false));
        return StrikeEvaluator.estimate(board.test.game, board.test.nekro, strike);
    }

    @Test
    void theTechIsRecognisedForItsOwnerOnly() {
        assertThat(L4Disruptors.shieldsInvaders(board.test.nekro)).isFalse();

        board.test.nekro.addTech("l4");

        assertThat(L4Disruptors.shieldsInvaders(board.test.nekro)).isTrue();
        assertThat(L4Disruptors.shieldsInvaders(board.sardakk)).isFalse();
    }

    // Without it the PDS shoots down landers, so more than one ground force is sent to make sure one lands.
    @Test
    void landsOnlyAsManyAsTheFightNeedsWhenNoPdsCanShoot() {
        int unshielded = landing().groundSent().get("lodor");
        board.test.nekro.addTech("l4");

        StrikeEstimate shielded = landing();

        assertThat(unshielded).isGreaterThanOrEqualTo(2);
        assertThat(shielded.groundSent().get("lodor")).isEqualTo(1);
    }

    @Test
    void doesNotCountTheCannonAgainstTheLandersLostInTheInvasion() {
        double unshielded = landing().ourExpectedLoss();
        board.test.nekro.addTech("l4");

        assertThat(landing().ourExpectedLoss()).isLessThan(unshielded);
    }

    private double take(Player enemy) {
        return new DefenseOutlook(board.test.game, board.test.nekro)
                .chances(enemy, board.home, Horizon.IMMEDIATE, board.defenders())
                .take();
    }

    // The threat from a neighbour with L4 Disruptors is higher: the PDS on the defended planet cannot shoot its
    // infantry.
    @Test
    void anInvaderWithL4IsMoreDangerousToAPlanetDefendedByPds() {
        board.raidingFleet(1, 2, 4);
        board.test.units(board.home, HOME_PLANET, board.test.nekro, UnitType.Infantry, 3);
        board.test.units(board.home, HOME_PLANET, board.test.nekro, UnitType.Pds, 2);
        double plain = take(board.sardakk);

        board.sardakk.addTech("l4");

        assertThat(take(board.sardakk)).isGreaterThan(plain);
    }
}
