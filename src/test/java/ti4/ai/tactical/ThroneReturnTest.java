package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import ti4.ai.scoring.ScoringStakes;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Game aiplay1: Hacan held Nekro's Support for the Throne, tied for the lead on 4 of 10, and attacked a Nekro system
// for a modest gain (a dreadnought, a mech and a planet), handing the point back. Returning the note loses a point
// already held, so a strike worth less than that point (here, taking a lightly held planet) must not be planned
// against the note's owner.
class ThroneReturnTest extends BaseTi4Test {

    private static final String HOME_PLANET = "mordaiii";

    private DefenseScenario board;
    private String target;

    private void modestStrikeOnSardakk() {
        board = new DefenseScenario();
        board.test.units(board.home, "space", board.test.nekro, UnitType.Carrier, 1);
        board.test.units(board.home, "space", board.test.nekro, UnitType.Dreadnought, 2);
        board.test.units(board.home, HOME_PLANET, board.test.nekro, UnitType.Infantry, 3);
        target = board.otherNeighbourOfHome();
        Tile lodor = board.test.place("26", target);
        board.sardakk.addPlanet("lodor");
        board.test.units(lodor, "lodor", board.sardakk, UnitType.Infantry, 1);
        board.test.points(board.sardakk, 4);
    }

    private void nekroHoldsSardakksThrone() {
        String note = board.sardakk.getColor() + "_sftt";
        board.sardakk.addOwnedPromissoryNoteByID(note);
        board.test.nekro.setPromissoryNote(note);
        board.test.nekro.addPromissoryNoteToPlayArea(note);
    }

    private Optional<TacticalPlan> attack() {
        return TacticalPlanner.forTarget(board.test.game, board.test.nekro, target)
                .filter(plan -> plan.kind() == Kind.ATTACK);
    }

    @Test
    void doesNotHandBackTheThroneForAStrikeWorthLessThanThePoint() {
        modestStrikeOnSardakk();
        board.test.points(board.test.nekro, 4);
        Player nekro = board.test.nekro;
        double strike = attack().map(TacticalPlan::score).orElse(0.0);
        double point = ScoringStakes.vpWorth(board.test.game, nekro);

        // Scenario guard: without the note the strike is worth taking, but it is worth less than a full point.
        assertThat(strike).isGreaterThan(TacticalPlanner.MIN_SCORE).isLessThan(point);

        modestStrikeOnSardakk();
        board.test.points(board.test.nekro, 3);
        nekroHoldsSardakksThrone();
        assertThat(board.test.nekro.getTotalVictoryPoints()).isEqualTo(4);

        assertThat(attack().map(TacticalPlan::score).orElse(0.0)).isLessThan(TacticalPlanner.MIN_SCORE);
        assertThat(TacticalPlanner.ranked(board.test.game, board.test.nekro))
                .noneMatch(plan -> plan.kind() == Kind.ATTACK && target.equals(plan.target()));
    }

    // Game aiplay1 round 4: Sol held Sardakk's Support for the Throne with Spark a Rebellion in hand, both tied for
    // the lead on 4 of 10. Sol attacked a lone Sardakk carrier, handing the throne back (-1) and scoring Spark a
    // Rebellion (+1): no change in points, and the secret, which could have been scored in a later combat, was spent.
    private void loneCarrierOfTheLeader() {
        loneSardakkCarrier("sar");
    }

    private void loneSardakkCarrier(String secret) {
        board = new DefenseScenario();
        board.test.units(board.home, "space", board.test.nekro, UnitType.Carrier, 1);
        board.test.units(board.home, "space", board.test.nekro, UnitType.Dreadnought, 2);
        board.test.units(board.home, "space", board.test.nekro, UnitType.Destroyer, 1);
        target = board.otherNeighbourOfHome();
        Tile open = board.test.place(DefenseScenario.EMPTY_SYSTEM, target);
        board.test.units(open, "space", board.sardakk, UnitType.Carrier, 1);
        board.test.nekro.setSecret(secret);
    }

    @Test
    void doesNotTradeTheThroneForASecretThatOnlyCancelsThePointOut() {
        loneCarrierOfTheLeader();
        board.test.points(board.sardakk, 4);
        board.test.points(board.test.nekro, 4);

        // Scenario guard: without the note, scoring Spark a Rebellion against the leader is worth the attack.
        assertThat(attack().map(TacticalPlan::score).orElse(0.0)).isGreaterThan(TacticalPlanner.MIN_SCORE);

        loneCarrierOfTheLeader();
        board.test.points(board.sardakk, 4);
        board.test.points(board.test.nekro, 3);
        nekroHoldsSardakksThrone();
        assertThat(board.test.nekro.getTotalVictoryPoints()).isEqualTo(4);

        assertThat(attack().map(TacticalPlan::score).orElse(0.0)).isLessThan(TacticalPlanner.MIN_SCORE);
        assertThat(TacticalPlanner.ranked(board.test.game, board.test.nekro))
                .noneMatch(plan -> plan.kind() == Kind.ATTACK && target.equals(plan.target()));
    }

    // Nekro holds Sardakk's Support for the Throne on 9 of 10. Attacking Sardakk returns the note (down to 8), so the
    // attack only wins when it scores two secrets: one in space combat and one in ground combat, or one in space combat
    // and Make an Example of Their World from the bombardment.
    private void guardedPlanetOfTheThroneOwner(int defenders) {
        board = new DefenseScenario();
        board.test.units(board.home, "space", board.test.nekro, UnitType.Carrier, 1);
        board.test.units(board.home, "space", board.test.nekro, UnitType.Dreadnought, 2);
        board.test.units(board.home, "space", board.test.nekro, UnitType.Destroyer, 1);
        board.test.units(board.home, HOME_PLANET, board.test.nekro, UnitType.Infantry, 4);
        target = board.otherNeighbourOfHome();
        Tile lodor = board.test.place("26", target);
        board.sardakk.addPlanet("lodor");
        board.test.units(lodor, "space", board.sardakk, UnitType.Destroyer, 1);
        board.test.units(lodor, "lodor", board.sardakk, UnitType.Infantry, defenders);
        board.test.points(board.sardakk, 6);
        board.test.points(board.test.nekro, 8);
        nekroHoldsSardakksThrone();
        assertThat(board.test.nekro.getTotalVictoryPoints()).isEqualTo(9);
    }

    @Test
    void doesNotHandBackTheThroneAtNineForOneSecret() {
        loneSardakkCarrier("dyp");
        board.test.points(board.sardakk, 6);
        board.test.points(board.test.nekro, 8);
        nekroHoldsSardakksThrone();
        assertThat(board.test.nekro.getTotalVictoryPoints()).isEqualTo(9);

        assertThat(attack().map(TacticalPlan::score).orElse(0.0)).isLessThan(TacticalPlanner.MIN_SCORE);
        assertThat(TacticalPlanner.ranked(board.test.game, board.test.nekro))
                .noneMatch(plan -> plan.kind() == Kind.ATTACK && target.equals(plan.target()));
    }

    // The bigger strike on the guarded planet used to clear the bar on one secret: the secret cancels the returned
    // point, so it ends the action on the same score and its point must count for nothing.
    @Test
    void doesNotHandBackTheThroneAtNineForOneSecretOnABiggerStrike() {
        guardedPlanetOfTheThroneOwner(1);
        board.test.nekro.setSecret("dyp");

        assertThat(attack().map(TacticalPlan::score).orElse(0.0)).isLessThan(TacticalPlanner.MIN_SCORE);
        assertThat(TacticalPlanner.ranked(board.test.game, board.test.nekro))
                .noneMatch(plan -> plan.kind() == Kind.ATTACK && target.equals(plan.target()));
    }

    @Test
    void handsBackTheThroneAtNineWhenSpaceAndGroundSecretsWin() {
        guardedPlanetOfTheThroneOwner(3);
        board.test.nekro.setSecret("dyp");
        board.test.nekro.setSecret("baf");

        assertWinningAttack();
    }

    @Test
    void handsBackTheThroneAtNineWhenASpaceSecretAndMakeAnExampleWin() {
        guardedPlanetOfTheThroneOwner(1);
        board.test.nekro.setSecret("dyp");
        board.test.nekro.setSecret("mew");

        assertWinningAttack();
    }

    private void assertWinningAttack() {
        double point = ScoringStakes.vpWorth(board.test.game, board.test.nekro);
        assertThat(attack().map(TacticalPlan::score).orElse(0.0)).isGreaterThan(point);
        assertThat(TacticalPlanner.ranked(board.test.game, board.test.nekro))
                .first()
                .satisfies(plan -> {
                    assertThat(plan.kind()).isEqualTo(Kind.ATTACK);
                    assertThat(plan.target()).isEqualTo(target);
                });
    }

    // A returned Support for the Throne costs the point first, so only a note that costs no point (here the Alliance)
    // lets the secret end the game; that winning point is still worth spending the note on.
    @Test
    void stillSpendsAReturnedNoteWhenTheSecretWinsTheGame() {
        loneCarrierOfTheLeader();
        board.test.points(board.sardakk, 9);
        board.test.points(board.test.nekro, 9);
        String alliance = board.sardakk.getColor() + "_an";
        board.sardakk.addOwnedPromissoryNoteByID(alliance);
        board.test.nekro.setPromissoryNote(alliance);
        board.test.nekro.addPromissoryNoteToPlayArea(alliance);

        assertThat(attack().map(TacticalPlan::score).orElse(0.0))
                .isGreaterThan(ScoringStakes.vpWorth(board.test.game, board.test.nekro));
    }
}
