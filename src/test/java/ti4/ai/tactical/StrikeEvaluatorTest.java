package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// The physics of a strike: what happens to both sides in the space combat and on the planets, before any value is put
// on it. Boards are clear-cut; the assertions are relations and single terms.
class StrikeEvaluatorTest extends BaseTi4Test {

    private static final String HOME_PLANET = "mordaiii";

    private DefenseScenario board;
    private Tile target;
    private Planet lodor;

    @BeforeEach
    void setUp() {
        board = new DefenseScenario();
        board.test.nekro.setFleetCC(10);
        target = board.test.place("26", board.otherNeighbourOfHome());
        lodor = target.getPlanetUnitHolders().getFirst();
        board.sardakk.addPlanet("lodor");
    }

    private Player nekro() {
        return board.test.nekro;
    }

    private Player sardakk() {
        return board.sardakk;
    }

    private void owns(Player player, Tile tile, String holder, UnitType type, int count) {
        board.test.units(tile, holder, player, type, count);
    }

    private static StrikeRules rules(boolean structures, boolean homeLandings, boolean custodians) {
        return new StrikeRules(false, 0.5, structures, homeLandings, custodians);
    }

    private StrikeEstimate estimate(StrikeRules rules, Object... fleet) {
        List<UnitMove> moves = new ArrayList<>();
        for (int i = 0; i < fleet.length; i += 2) {
            UnitType type = (UnitType) fleet[i];
            int count = (Integer) fleet[i + 1];
            String holder = type == UnitType.Infantry || type == UnitType.Mech ? HOME_PLANET : "space";
            moves.add(new UnitMove(AiTestGame.HOME, holder, type, count));
        }
        Strike strike = Strike.of(nekro(), target, sardakk(), List.of(board.home), moves, rules);
        return StrikeEvaluator.estimate(board.test.game, nekro(), strike);
    }

    // Test 10: nothing defends the planet, but the PDS on it shoots at the landing, so more than one ground force goes.
    // (The same PDS also shoots a ship out of the fleet on arrival, so there are two carriers.)
    @Test
    void anUndefendedPlanetWithAPdsGetsMoreThanOneLander() {
        owns(sardakk(), target, "lodor", UnitType.Pds, 1);

        StrikeEstimate estimate = estimate(rules(true, true, false), UnitType.Carrier, 2, UnitType.Infantry, 4);

        assertThat(estimate.groundSent().get("lodor")).isGreaterThanOrEqualTo(2);
        assertThat(estimate.landings()).containsKey("lodor");
        assertThat(estimate.ourExpectedLoss()).isPositive();
    }

    // The defending PDS fires its space cannon defense at the committed ground forces before the ground combat, so
    // the same landing party wins less often than against the bare planet.
    @Test
    void aPdsOnTheDefendedPlanetShootsTheLandingBeforeTheGroundCombat() {
        owns(sardakk(), target, "lodor", UnitType.Infantry, 1);
        double bare = estimate(rules(true, true, false), UnitType.Carrier, 2, UnitType.Infantry, 3)
                .groundWins()
                .get("lodor");

        owns(sardakk(), target, "lodor", UnitType.Pds, 1);
        StrikeEstimate guarded = estimate(rules(true, true, false), UnitType.Carrier, 2, UnitType.Infantry, 3);

        assertThat(guarded.groundWins().get("lodor")).isLessThan(bare);
        assertThat(guarded.ourExpectedLoss()).isPositive();
    }

    @Test
    void aPlanetWithStructuresIsLeftAloneUnlessTheRulesAllowIt() {
        owns(sardakk(), target, "lodor", UnitType.Pds, 1);

        StrikeEstimate estimate = estimate(rules(false, true, false), UnitType.Carrier, 1, UnitType.Infantry, 4);

        assertThat(estimate.landings()).isEmpty();
    }

    @Test
    void takingAPlanetDestroysItsStructures() {
        owns(sardakk(), target, "lodor", UnitType.Infantry, 1);
        double without = estimate(rules(true, true, false), UnitType.Carrier, 1, UnitType.Infantry, 4)
                .theirExpectedLoss();

        owns(sardakk(), target, "lodor", UnitType.Spacedock, 1);
        double with = estimate(rules(true, true, false), UnitType.Carrier, 1, UnitType.Infantry, 4)
                .theirExpectedLoss();

        assertThat(with - without).isBetween(3.0, 4.01);
    }

    @Test
    void anotherPlayersHomeIsOnlyLandedOnWhenTheRulesSayHomeLandingsAreOn() {
        Tile sardakkHome = board.test.game.getTileByPosition(DefenseScenario.SARDAKK_HOME_POSITION);
        owns(sardakk(), sardakkHome, "quinarra", UnitType.Infantry, 1);
        target = sardakkHome;

        StrikeEstimate on = estimate(rules(true, true, false), UnitType.Carrier, 1, UnitType.Infantry, 4);
        StrikeEstimate off = estimate(rules(true, false, false), UnitType.Carrier, 1, UnitType.Infantry, 4);

        assertThat(on.landings()).isNotEmpty();
        assertThat(off.landings()).isEmpty();
        assertThat(off.spaceWin()).isEqualTo(1.0);
    }

    @Test
    void aFreePlanetWithTheCustodiansTokenNeedsThePayment() {
        Tile rex = board.test.place("18", "000");
        Planet mecatol = rex.getPlanetUnitHolders().getFirst();
        mecatol.addToken(Constants.CUSTODIAN_TOKEN_PNG);
        sardakk().removePlanet("lodor");
        owns(sardakk(), rex, "space", UnitType.Destroyer, 1);
        target = rex;

        StrikeEstimate unpaid = estimate(rules(false, true, false), UnitType.Carrier, 1, UnitType.Infantry, 2);
        StrikeEstimate paid = estimate(rules(false, true, true), UnitType.Carrier, 1, UnitType.Infantry, 2);

        assertThat(unpaid.landings()).isEmpty();
        assertThat(paid.landings()).containsKey(mecatol.getName());
    }

    @Test
    void aFreePlanetInTheSystemIsTakenAfterTheDefendedOnes() {
        owns(sardakk(), target, "space", UnitType.Destroyer, 1);
        sardakk().removePlanet("lodor");

        StrikeEstimate estimate = estimate(rules(false, true, false), UnitType.Carrier, 1, UnitType.Infantry, 2);

        assertThat(estimate.landings()).containsKey("lodor");
        assertThat(estimate.groundWins().get("lodor")).isEqualTo(1.0);
    }

    @Test
    void landingsAreTotalsThatIncludeForcesAlreadyOnThePlanet() {
        owns(nekro(), target, "lodor", UnitType.Infantry, 1);

        StrikeEstimate estimate = estimate(rules(false, true, false), UnitType.Carrier, 1, UnitType.Infantry, 2);

        assertThat(estimate.landings().get("lodor"))
                .isEqualTo(1 + estimate.groundSent().get("lodor"));
    }

    // Four PDS II cover the system: the expected hits are enough to destroy the whole small fleet before it fights.
    @Test
    void enoughSpaceCannonDestroysTheFleetAndTheCargo() {
        sardakk().removeOwnedUnitByID("pds");
        sardakk().addOwnedUnitByID("pds2");
        owns(sardakk(), target, "lodor", UnitType.Pds, 4);

        StrikeEstimate estimate = estimate(rules(true, true, false), UnitType.Carrier, 1, UnitType.Cruiser, 1);

        assertThat(estimate.spaceWin()).isZero();
        assertThat(estimate.ourLossChance()).isEqualTo(1.0);
        assertThat(estimate.ourExpectedLoss()).isGreaterThanOrEqualTo(5.0);
    }

    // The cheapest ship goes first, so with one hit expected the carrier survives, but a surviving carrier's capacity
    // is
    // all that can land: two carriers lose one, and half the cargo with it.
    @Test
    void cargoOfADestroyedCarrierNeverLands() {
        owns(sardakk(), target, "lodor", UnitType.Infantry, 1);
        owns(sardakk(), target, "lodor", UnitType.Pds, 1);

        StrikeEstimate estimate = estimate(rules(true, true, false), UnitType.Carrier, 2, UnitType.Infantry, 8);

        assertThat(estimate.survivors().get(UnitType.Carrier)).isEqualTo(1);
        assertThat(estimate.groundSent().get("lodor")).isLessThanOrEqualTo(4);
        assertThat(estimate.ourExpectedLoss()).isGreaterThan(3.0);
    }

    @Test
    void anOutgunnedFleetLosesItsCargoToo() {
        owns(sardakk(), target, "space", UnitType.Dreadnought, 3);

        StrikeEstimate estimate = estimate(rules(false, true, false), UnitType.Carrier, 1, UnitType.Infantry, 3);

        assertThat(estimate.spaceWin()).isLessThan(0.1);
        assertThat(estimate.landings()).isEmpty();
        assertThat(estimate.ourExpectedLoss()).isGreaterThan(3.0 + 3 * 0.5 - 0.5);
    }

    private Player naaluStack(UnitType type, int ships) {
        Player naalu = board.test.addSeat("200000000000000005", "naalu", "yellow");
        naalu.setStrategicCC(2);
        sardakk().removePlanet("lodor");
        owns(naalu, target, "space", type, ships);
        board.test.place("48", freeNeighbour(target));
        return naalu;
    }

    private StrikeEstimate against(Player opponent, StrikeRules rules, Object... fleet) {
        List<UnitMove> moves = new ArrayList<>();
        for (int i = 0; i < fleet.length; i += 2) {
            moves.add(new UnitMove(AiTestGame.HOME, "space", (UnitType) fleet[i], (Integer) fleet[i + 1]));
        }
        Strike strike = Strike.of(nekro(), target, opponent, List.of(board.home), moves, rules);
        return StrikeEvaluator.estimate(board.test.game, nekro(), strike);
    }

    // Test 12: a Foresight stack that would lose slips away to an empty neighbour: no combat, no kills, and it is still
    // there afterwards.
    @Test
    void foresightMovesAStackThatWouldLoseBeforeTheCombat() {
        Player naalu = naaluStack(UnitType.Cruiser, 3);

        StrikeEstimate estimate =
                against(naalu, rules(false, true, false), UnitType.Dreadnought, 2, UnitType.Carrier, 1);

        assertThat(estimate.escaped()).isTrue();
        assertThat(estimate.spaceCombat()).isFalse();
        assertThat(estimate.theirExpectedLoss()).isZero();
        assertThat(estimate.spaceKillChance()).isZero();
        assertThat(estimate.ourExpectedLoss()).isZero();
    }

    @Test
    void foresightNeedsAStrategyTokenAndAnEmptyNeighbour() {
        Player naalu = naaluStack(UnitType.Cruiser, 3);
        naalu.setStrategicCC(0);

        StrikeEstimate withoutToken =
                against(naalu, rules(false, true, false), UnitType.Dreadnought, 2, UnitType.Carrier, 1);

        assertThat(withoutToken.escaped()).isFalse();
        assertThat(withoutToken.spaceCombat()).isTrue();
        assertThat(withoutToken.theirExpectedLoss()).isPositive();
    }

    // Test 12b: a stack that would win stays and fights.
    @Test
    void foresightIsNotUsedByAStackThatWouldWin() {
        Player naalu = naaluStack(UnitType.Dreadnought, 3);

        StrikeEstimate estimate = against(naalu, rules(false, true, false), UnitType.Carrier, 1, UnitType.Destroyer, 1);

        assertThat(estimate.escaped()).isFalse();
        assertThat(estimate.spaceWin()).isLessThan(0.3);
    }

    private String freeNeighbour(Tile tile) {
        return ti4.image.PositionMapper.getAdjacentTilePositions(tile.getPosition()).stream()
                .filter(position -> !"x".equals(position) && board.test.game.getTileByPosition(position) == null)
                .findFirst()
                .orElseThrow();
    }
}
