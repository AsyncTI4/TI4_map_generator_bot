package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Styx (frac4) is guarded by two neutral dreadnoughts, a destroyer and three infantry. frac3 and frac5 lie on either
// side of it. A one-action strike may pool warships from one system with a carrier and its infantry from another
// (LRR 95.1: each ship picks up from its own system), so the warships clear space and the infantry land the same
// action.
class StyxTransportStrikeTest extends BaseTi4Test {

    private FractureScenario fracture;

    @BeforeEach
    void setUp() {
        fracture = new FractureScenario();
    }

    private Game game() {
        return fracture.board.test.game;
    }

    private Player nekro() {
        return fracture.nekro();
    }

    private Tile styx() {
        return fracture.fracture(FractureScenario.STYX);
    }

    private TacticalPlanner.Context context() {
        return new TacticalPlanner.Context(game(), nekro());
    }

    private List<TacticalPlanner.Candidate> pool(TacticalPlanner.Context context) {
        return TacticalPlanner.candidatePool(context, styx(), game().getNeutral());
    }

    private static boolean moves(TacticalPlanner.Candidate candidate, String origin, UnitType type) {
        return candidate.moves().stream()
                .anyMatch(move -> move.origin().equals(origin) && move.type() == type && move.count() > 0);
    }

    private static int count(TacticalPlanner.Candidate candidate, UnitType type) {
        return candidate.moves().stream()
                .filter(move -> move.type() == type)
                .mapToInt(UnitMove::count)
                .sum();
    }

    private static int nonFighterShips(TacticalPlanner.Candidate candidate) {
        return candidate.moves().stream()
                .filter(move -> ti4.ai.eval.BoardView.MOVING_SHIPS.contains(move.type()))
                .mapToInt(UnitMove::count)
                .sum();
    }

    private static double groundWin(TacticalPlanner.Candidate candidate) {
        return candidate.estimate().groundWins().values().stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0);
    }

    private Optional<TacticalPlanner.Candidate> landingWith(List<TacticalPlanner.Candidate> pool, String origin) {
        return pool.stream()
                .filter(found -> moves(found, origin, UnitType.Infantry) && moves(found, "frac3", UnitType.Dreadnought))
                .max(java.util.Comparator.comparingDouble(found -> found.quick().score()));
    }

    @Test
    void aPlanetStrikeMergesACarrierFromAnotherSystem() {
        nekro().setFleetCC(7);
        fracture.ships("frac3", UnitType.Dreadnought, 5);
        fracture.ships("frac3", UnitType.Cruiser, 1);
        fracture.ships("frac5", UnitType.Carrier, 1);
        fracture.ships("frac5", UnitType.Infantry, 4);

        Optional<TacticalPlanner.Candidate> merged = landingWith(pool(context()), "frac5");

        assertThat(merged).isPresent();
        assertThat(moves(merged.get(), "frac5", UnitType.Carrier)).isTrue();
        assertThat(groundWin(merged.get())).isPositive();
    }

    // Seven ships do not fit a fleet pool of six (LRR 37), so the merge leaves the cheapest warship that carries
    // nothing at home instead of giving up the landing.
    @Test
    void reSplitsToFitTheFleetSupply() {
        nekro().setFleetCC(6);
        fracture.ships("frac3", UnitType.Dreadnought, 4);
        fracture.ships("frac3", UnitType.Destroyer, 2);
        fracture.ships("frac5", UnitType.Carrier, 1);
        fracture.ships("frac5", UnitType.Infantry, 4);

        TacticalPlanner.Candidate merged = landingWith(pool(context()), "frac5").orElseThrow();

        assertThat(nonFighterShips(merged)).isLessThanOrEqualTo(6);
        assertThat(count(merged, UnitType.Destroyer)).isEqualTo(1);
        assertThat(count(merged, UnitType.Dreadnought)).isEqualTo(4);
        assertThat(moves(merged, "frac5", UnitType.Carrier)).isTrue();
    }

    // Two warship systems and a fleet pool of five: frac3 and frac5 together hold seven ships, more than the pool.
    // That overflow no longer ends the search, so frac3's dreadnoughts still merge with the carrier and infantry at
    // frac5, re-split to fit, and nothing in the pool breaks the fleet pool.
    @Test
    void combinationsTryEveryOriginAfterAnOverflow() {
        nekro().setFleetCC(5);
        fracture.ships("frac3", UnitType.Dreadnought, 3);
        fracture.ships("frac5", UnitType.Dreadnought, 3);
        fracture.ships("frac5", UnitType.Carrier, 1);
        fracture.ships("frac5", UnitType.Infantry, 4);

        List<TacticalPlanner.Candidate> pool = pool(context());

        assertThat(pool).allMatch(found -> nonFighterShips(found) <= 5);
        assertThat(pool)
                .anyMatch(found -> found.parts().size() >= 2
                        && moves(found, "frac5", UnitType.Carrier)
                        && moves(found, "frac3", UnitType.Dreadnought));
    }

    // A carrier with infantry cannot win the space fight alone, so no strike of its own is ever worth making, but it
    // still joins the warships that can.
    @Test
    void aLoneTransportThatCannotWinSpaceStillJoins() {
        fracture.ships("frac3", UnitType.Dreadnought, 4);
        fracture.ships("frac5", UnitType.Carrier, 1);
        fracture.ships("frac5", UnitType.Infantry, 2);
        TacticalPlanner.Context context = context();

        List<TacticalPlanner.Candidate> pool = pool(context);

        assertThat(pool.stream()
                        .filter(found -> found.parts().size() == 1 && moves(found, "frac5", UnitType.Carrier))
                        .filter(found ->
                                TacticalPlanner.fullPlan(context, styx(), found).isPresent()))
                .isEmpty();
        assertThat(landingWith(pool, "frac5")).isPresent();
    }

    // Warships that clear Styx's space this action leave the planet for next round: ground forces can be built at
    // the home dock and brought in then. That later landing is worth something, not nothing.
    // Ships that clear Styx's space stay in Styx and cannot fetch ground forces (LRR 95.1), so a clear earns a later
    // landing only from ships elsewhere that can arrive next round, here a carrier at frac5.
    @Test
    void aSpaceClearEarnsALaterLandingOnlyFromShipsThatCanArriveNextRound() {
        fracture.ships("frac3", UnitType.Dreadnought, 4);
        assertThat(clearWithoutFrac5(pool(context())).terms().laterLanding()).isZero();

        fracture.ships("frac5", UnitType.Carrier, 1);
        TacticalPlanner.Candidate clear = clearWithoutFrac5(pool(context()));
        assertThat(clear.terms().laterLanding()).isPositive();
        assertThat(clear.quick().score()).isPositive();
    }

    private static TacticalPlanner.Candidate clearWithoutFrac5(List<TacticalPlanner.Candidate> pool) {
        return pool.stream()
                .filter(found -> found.estimate().landings().isEmpty() && moves(found, "frac3", UnitType.Dreadnought))
                .filter(found -> !moves(found, "frac5", UnitType.Carrier))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void aSpaceClearIsWorthLessThanTheSameStrikeLandingNow() {
        fracture.ships("frac3", UnitType.Dreadnought, 4);
        fracture.ships("frac5", UnitType.Carrier, 1);
        fracture.ships("frac5", UnitType.Infantry, 4);

        List<TacticalPlanner.Candidate> pool = pool(context());
        TacticalPlanner.Candidate clear = pool.stream()
                .filter(found -> found.estimate().landings().isEmpty() && moves(found, "frac3", UnitType.Dreadnought))
                .filter(found -> !moves(found, "frac5", UnitType.Carrier))
                .findFirst()
                .orElseThrow();
        TacticalPlanner.Candidate landing = landingWith(pool, "frac5").orElseThrow();

        assertThat(clear.terms().laterLanding()).isPositive();
        assertThat(landing.terms().laterLanding()).isZero();
        assertThat(clear.quick().score()).isLessThan(landing.quick().score());
    }
}
