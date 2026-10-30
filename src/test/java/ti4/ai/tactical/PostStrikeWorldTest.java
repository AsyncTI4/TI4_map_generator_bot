package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.tactical.DefenseOutlook.Blend;
import ti4.ai.tactical.IncomingForces.Exclusion;
import ti4.ai.tactical.IncomingForces.Horizon;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Planet;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// The board as it would be if a strike won: the struck player's units in the target are gone, so what they could
// bring against the seat's guarded systems, and against the target itself, shrinks.
class PostStrikeWorldTest extends BaseTi4Test {

    private static final String HOME_PLANET = "mordaiii";
    private static final int IMPERIAL = 8;

    private DefenseScenario board;

    @BeforeEach
    void setUp() {
        board = new DefenseScenario();
    }

    private DefenseOutlook outlook() {
        return new DefenseOutlook(board.test.game, board.test.nekro);
    }

    private Exclusion stagingFleetGone() {
        return new Exclusion(board.sardakk, board.staging);
    }

    private void garrison(int infantry) {
        board.test.units(board.home, HOME_PLANET, board.test.nekro, UnitType.Infantry, infantry);
    }

    @Test
    void anExcludedFleetIsNeitherAnOriginNorAResident() {
        board.raidingFleet(1, 2, 4);
        IncomingForces whole = new IncomingForces(board.test.game);
        IncomingForces without = new IncomingForces(board.test.game, stagingFleetGone());

        assertThat(whole.of(board.sardakk, board.home, Horizon.IMMEDIATE).isEmpty())
                .isFalse();
        assertThat(without.of(board.sardakk, board.home, Horizon.IMMEDIATE).isEmpty())
                .isTrue();
        assertThat(whole.of(board.sardakk, board.staging, Horizon.IMMEDIATE).isEmpty())
                .isFalse();
        assertThat(without.of(board.sardakk, board.staging, Horizon.IMMEDIATE).isEmpty())
                .isTrue();
    }

    @Test
    void exclusionOnlyRemovesTheNamedPlayersUnitsInThatTile() {
        board.raidingFleet(1, 2, 4);
        Tile elsewhere = board.test.place("47", board.otherNeighbourOfHome());
        board.test.units(elsewhere, "space", board.sardakk, UnitType.Cruiser, 2);
        IncomingForces without = new IncomingForces(board.test.game, stagingFleetGone());

        assertThat(without.of(board.sardakk, board.home, Horizon.IMMEDIATE).ships())
                .containsEntry(UnitType.Cruiser, 2)
                .doesNotContainKey(UnitType.Carrier);
    }

    // Excluding the fleet that threatens a system the strike does not leave from lowers that system's cost; the
    // excluded outlook is memoised and takes the guarded status from the base outlook.
    @Test
    void excludingTheThreateningFleetLowersTheCostOfAGuardedSystem() {
        board.raidingFleet(1, 2, 4);
        board.reveal("conquer");
        garrison(1);
        DefenseOutlook base = outlook();
        DefenseOutlook excluded = base.excluding(stagingFleetGone());

        assertThat(base.cost(board.home)).isPositive();
        assertThat(excluded.cost(board.home)).isZero();
        assertThat(excluded.isGuarded(board.home)).isEqualTo(base.isGuarded(board.home));
        assertThat(base.excluding(stagingFleetGone())).isSameAs(excluded);
    }

    // Test 14: the struck fleet is excluded from the counterstrike, so a target where the opponent has nothing else
    // nearby draws none, and a target next to its big fleet draws some.
    @Test
    void theCounterstrikeIgnoresTheStruckFleetButNotItsNeighbours() {
        Tile isolated = board.test.place("26", board.beyondStaging());
        board.test.units(isolated, "space", board.sardakk, UnitType.Destroyer, 1);
        Exclusion struckDestroyer = new Exclusion(board.sardakk, isolated);
        Defenders after = Defenders.of(board.test.nekro, isolated).plusShips(UnitType.Destroyer, 1, 0);

        double withTheFleet = outlook().counterstrike(isolated, after, List.of(), 0.5, 0, board.sardakk);
        double withoutIt =
                outlook().excluding(struckDestroyer).counterstrike(isolated, after, List.of(), 0.5, 0, board.sardakk);

        assertThat(withTheFleet).isPositive();
        assertThat(withoutIt).isZero();

        board.raidingFleet(1, 2, 4);
        double nextToTheBigFleet =
                outlook().excluding(struckDestroyer).counterstrike(isolated, after, List.of(), 0.5, 0, board.sardakk);
        assertThat(nextToTheBigFleet).isPositive();
    }

    @Test
    void aSystemTheSeatGuardsHasNoCounterstrikeTerm() {
        board.raidingFleet(1, 2, 4);
        Defenders after = Defenders.of(board.test.nekro, board.home);

        assertThat(outlook().counterstrike(board.home, after, List.of(), 5, 5, board.sardakk))
                .isZero();
    }

    @Test
    void theStruckPlayerIsEagerToRetaliate() {
        board.raidingFleet(1, 2, 4);
        Tile target = board.test.place("26", board.beyondStaging());
        Defenders after = Defenders.of(board.test.nekro, target).plusShips(UnitType.Destroyer, 1, 0);
        var third = board.test.addSeat("200000000000000003", "hacan", "green");

        double struck = outlook().counterstrike(target, after, List.of(), 1, 0, board.sardakk);
        double bystander = outlook().counterstrike(target, after, List.of(), 1, 0, third);

        assertThat(struck).isGreaterThanOrEqualTo(bystander);
    }

    @Test
    void chancesAgainstAnExplicitPlanetListIgnoresThePlanetsTheSeatHoldsNow() {
        board.raidingFleet(1, 2, 4);
        Tile lodor = board.test.place("26", board.beyondStaging());
        Planet planet = lodor.getPlanetUnitHolders().getFirst();
        Defenders after = Defenders.of(board.test.nekro, lodor).plusGround(planet.getName(), UnitType.Infantry, 1);

        DefenseOutlook.Chances held =
                outlook().chancesAgainst(board.sardakk, lodor, Horizon.IMMEDIATE, after, List.of(planet));
        DefenseOutlook.Chances none =
                outlook().chancesAgainst(board.sardakk, lodor, Horizon.IMMEDIATE, after, List.of());

        assertThat(held.take()).isGreaterThan(0.5);
        assertThat(held.space()).isGreaterThan(0.9);
        assertThat(none.take()).isZero();
        assertThat(none.space()).isEqualTo(held.space());
    }

    // The two-sided departure model: when the strike removes the threat, every ship may leave; when it does not, the
    // plain model holds one back.
    @Test
    void aBlendHoldsBackLessWhenTheStrikeRemovesTheThreat() {
        board.raidingFleet(1, 1, 4);
        board.reveal("conquer");
        board.test.units(board.home, "space", board.test.nekro, UnitType.Dreadnought, 2);
        board.test.units(board.home, "space", board.test.nekro, UnitType.Fighter, 2);
        garrison(3);
        DefenseOutlook base = outlook();
        List<UnitMove> candidates = List.of(new UnitMove(AiTestGame.HOME, "space", UnitType.Dreadnought, 2));
        DefenseOutlook excluded = base.excluding(stagingFleetGone());

        List<UnitMove> plain = base.holdBack(board.home, List.of(), candidates);
        List<UnitMove> sure = base.holdBack(board.home, List.of(), candidates, new Blend(excluded, 1.0));
        List<UnitMove> never = base.holdBack(board.home, List.of(), candidates, new Blend(excluded, 0.0));

        assertThat(count(plain)).isEqualTo(1);
        assertThat(count(sure)).isEqualTo(2);
        assertThat(count(never)).isEqualTo(count(plain));
    }

    @Test
    void aBlendLowersTheCostOfLeaving() {
        board.raidingFleet(1, 1, 4);
        board.reveal("conquer");
        board.test.units(board.home, "space", board.test.nekro, UnitType.Dreadnought, 2);
        garrison(3);
        DefenseOutlook base = outlook();
        List<UnitMove> leaving = List.of(new UnitMove(AiTestGame.HOME, "space", UnitType.Dreadnought, 2));
        Blend blend = new Blend(base.excluding(stagingFleetGone()), 0.8);

        double plain = base.leaveCost(board.home, leaving);
        double blended = base.leaveCost(board.home, leaving, blend);

        assertThat(blended).isLessThan(plain);
    }

    @Test
    void aBlendKeepsFewerGroundForcesAtHomeWhenTheStrikeRemovesTheThreat() {
        board.raidingFleet(1, 2, 4);
        board.reveal("conquer");
        garrison(5);
        DefenseOutlook base = outlook();
        Planet planet = board.home.getPlanetUnitHolders().getFirst();

        int plain = base.groundToKeep(board.home, planet, List.of());
        int blended =
                base.groundToKeep(board.home, planet, List.of(), new Blend(base.excluding(stagingFleetGone()), 1.0));

        assertThat(blended).isLessThan(plain);
    }

    // Mecatol Rex held by the seat while another player holds the unplayed Imperial card hands that player a point if
    // it falls: the system is worth that much more to keep.
    @Test
    void losingMecatolToTheImperialHolderRaisesTheStake() {
        Tile mecatol = board.test.place("18", "000");
        board.test.nekro.addPlanet("mr");
        double without = outlook().stake(mecatol);

        board.sardakk.addSC(IMPERIAL);

        assertThat(outlook().stake(mecatol)).isGreaterThan(without);
    }

    private static int count(List<UnitMove> moves) {
        return moves.stream().mapToInt(UnitMove::count).sum();
    }
}
