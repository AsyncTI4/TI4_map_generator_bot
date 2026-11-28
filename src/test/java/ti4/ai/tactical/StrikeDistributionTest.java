package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.ai.eval.CannonDice;
import ti4.ai.eval.DiceOdds;
import ti4.ai.eval.FracturePrize;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// The pre- and post-combat steps of StrikeEvaluator: neutral defenders are bombarded, dice are rolled as distributions
// instead of rounded expected hits, a weak landing still reports its take chance, and capacity after combat keeps the
// ground forces that land (LRR 16.3a lets the attacker pick which excess units to remove).
class StrikeDistributionTest extends BaseTi4Test {

    private static final String HACAN_ID = "200000000000000003";

    private static StrikeEstimate estimate(
            FractureScenario scenario, Player seat, Tile target, Player opponent, StrikeRules rules, Object... fleet) {
        Tile origin = scenario.board.staging;
        List<UnitMove> moves = new ArrayList<>();
        for (int i = 0; i < fleet.length; i += 2) {
            UnitType type = (UnitType) fleet[i];
            int count = (Integer) fleet[i + 1];
            scenario.board.test.units(origin, "space", seat, type, count);
            moves.add(new UnitMove(origin.getPosition(), "space", type, count));
        }
        Strike strike = Strike.of(seat, target, opponent, List.of(origin), moves, rules);
        return StrikeEvaluator.estimate(scenario.board.test.game, seat, strike);
    }

    @Test
    void neutralGroundForcesOnStyxAreABombardmentTarget() {
        FractureScenario scenario = new FractureScenario();
        Player hacan = scenario.board.test.addSeat(HACAN_ID, "hacan", "yellow");
        Tile styx = scenario.fracture(FractureScenario.STYX);

        var target = BombardmentRules.target(scenario.board.test.game, hacan, styx, Map.of(UnitType.Dreadnought, 1));

        assertThat(target).isPresent();
        assertThat(target.get().planet()).isEqualTo(FracturePrize.STYX);
        assertThat(target.get().defender().isNeutral()).isTrue();
    }

    @Test
    void oneDreadnoughtBombardsAsADistributionNotAFlooredZero() {
        FractureScenario scenario = new FractureScenario();
        Player hacan = scenario.board.test.addSeat(HACAN_ID, "hacan", "yellow");

        double[] hits = BombardmentRules.hitDistribution(hacan, Map.of(UnitType.Dreadnought, 1));

        assertThat(BombardmentRules.expectedHits(hacan, Map.of(UnitType.Dreadnought, 1)))
                .isZero();
        assertThat(hits).hasSize(2);
        assertThat(hits[1]).isCloseTo(0.6, within(1e-9));
    }

    @Test
    void neutralPdsFiresSpaceCannonAtArrivingShips() {
        FractureScenario scenario = new FractureScenario();
        Player hacan = scenario.board.test.addSeat(HACAN_ID, "hacan", "yellow");
        Player neutral = scenario.board.test.game.getNeutral();
        Tile styx = scenario.fracture(FractureScenario.STYX);
        List<Double> before = CannonDice.againstShipsOf(scenario.board.test.game, hacan, styx);

        scenario.board.test.units(styx, FracturePrize.STYX, neutral, UnitType.Pds, 1);

        assertThat(CannonDice.againstShipsOf(scenario.board.test.game, hacan, styx))
                .hasSize(before.size() + 1);
    }

    @Test
    void aCoinFlipSpaceCannonShotLeavesHalfTheChanceInsteadOfACertainWipe() {
        DefenseScenario board = new DefenseScenario();
        Tile target = board.test.place("26", board.otherNeighbourOfHome());
        board.sardakk.addPlanet("lodor");
        board.test.units(target, "lodor", board.sardakk, UnitType.Pds, 1);
        board.test.units(board.staging, "space", board.test.nekro, UnitType.Cruiser, 1);
        Strike strike = Strike.of(
                board.test.nekro,
                target,
                board.sardakk,
                List.of(board.staging),
                List.of(new UnitMove(board.staging.getPosition(), "space", UnitType.Cruiser, 1)),
                new StrikeRules(false, 0.5, true, true));
        double hit = 1 - DiceOdds.distribution(CannonDice.againstShipsOf(board.test.game, board.test.nekro, target))[0];

        StrikeEstimate estimate = StrikeEvaluator.estimate(board.test.game, board.test.nekro, strike);

        assertThat(estimate.spaceWin()).isCloseTo(1 - hit, within(1e-9));
        assertThat(estimate.ourExpectedLoss()).isCloseTo(hit * 2, within(1e-9));
    }

    @Test
    void aLandingBelowTheGateStillReportsItsTakeChance() {
        FractureScenario scenario = new FractureScenario();
        Player hacan = scenario.board.test.addSeat(HACAN_ID, "hacan", "yellow");
        Player neutral = scenario.board.test.game.getNeutral();
        Tile styx = scenario.fracture(FractureScenario.STYX);

        StrikeEstimate estimate = estimate(
                scenario,
                hacan,
                styx,
                neutral,
                new StrikeRules(false, 0.5, true, true),
                UnitType.Cruiser,
                4,
                UnitType.Carrier,
                1,
                UnitType.Infantry,
                1);

        assertThat(estimate.spaceWin()).isGreaterThan(0.1);
        assertThat(estimate.groundWins().get(FracturePrize.STYX)).isBetween(0.01, 0.5);
        assertThat(estimate.landings()).doesNotContainKey(FracturePrize.STYX);
        assertThat(estimate.groundSent()).isEmpty();
    }

    @Test
    void excessFightersAreRemovedBeforeTheGroundForcesThatLand() {
        DefenseScenario board = new DefenseScenario();
        Tile target = board.test.place("26", board.otherNeighbourOfHome());
        board.sardakk.addPlanet("lodor");
        board.test.units(target, "lodor", board.sardakk, UnitType.Infantry, 1);
        Player nekro = board.test.nekro;
        Strike strike = new Strike(
                target,
                board.sardakk,
                List.of(),
                List.of(),
                Map.of(UnitType.Carrier, 1, UnitType.Fighter, 4),
                Map.of(),
                Map.of(UnitType.Infantry, 4),
                Map.of(),
                Map.of(),
                new StrikeRules(false, 0.5, true, true),
                Map.of());

        StrikeEstimate estimate = StrikeEvaluator.afterSpaceWon(board.test.game, nekro, strike, 1.0);

        assertThat(estimate.groundSent()).containsKey("lodor");
        assertThat(estimate.groundSent().get("lodor")).isGreaterThanOrEqualTo(2);
        assertThat(estimate.groundWins().get("lodor")).isGreaterThan(0.5);
    }

    @Test
    void diceHelpersCapPruneAndScaleDistributions() {
        double[] two = DiceOdds.distribution(List.of(0.5, 0.5));
        assertThat(two).containsExactly(0.25, 0.5, 0.25);
        assertThat(DiceOdds.capped(two, 1)).containsExactly(0.25, 0.75);
        assertThat(DiceOdds.multiplied(two, 2)).containsExactly(0.25, 0, 0.5, 0, 0.25);
        double[] pruned = DiceOdds.pruned(new double[] {0.999_95, 0.000_05});
        assertThat(pruned[0]).isEqualTo(1.0);
        assertThat(pruned[1]).isZero();
    }
}
