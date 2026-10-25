package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

// A carrier that moves into a system holding a planet nobody controls should bring along the ground forces the
// system it leaves can spare and land them. The scenario is the reported one: Intimidate Council wants ships in two
// systems next to Mecatol Rex, a destroyer already sits in one (101), and the only way to the second (102) is the
// carrier from a ring-two system whose planet it can draw infantry from.
class CarrierCargoTest extends BaseTi4Test {

    private static final String DESTINATION = "102";

    private AiTestGame test;
    private Tile departure;
    private String departurePosition;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setRound(3);
        test.nekroHome();
        test.place("18", "000");
        test.place("46", "101");
        test.units(test.game.getTileByPosition("101"), "space", test.nekro, UnitType.Destroyer, 1);
        departurePosition = MapPositionMapper.getAdjacentTilePositions(DESTINATION).stream()
                .filter(position -> position.startsWith("2"))
                .findFirst()
                .orElseThrow();
        departure = test.place("25", departurePosition);
        test.nekro.addPlanet("quann");
        test.units(departure, "space", test.nekro, UnitType.Carrier, 1);
        test.game.getRevealedPublicObjectives().put("intimidate", 1);
    }

    private Optional<TacticalPlan> plan() {
        return TacticalPlanner.forTarget(test.game, test.nekro, DESTINATION);
    }

    private static int moved(TacticalPlan plan, UnitType type) {
        return plan.moves().stream()
                .filter(move -> move.type() == type)
                .mapToInt(UnitMove::count)
                .sum();
    }

    private Tile twoPlanetSystem() {
        return test.place("27", DESTINATION);
    }

    private void enemyHolds(Tile tile, String planet, int infantry, int mechs) {
        test.sol.addPlanet(planet);
        test.units(tile, planet, test.sol, UnitType.Infantry, infantry);
        test.units(tile, planet, test.sol, UnitType.Mech, mechs);
    }

    // The ordinary case, already handled by the expansion plan: the carrier carries the infantry and lands them.
    @Test
    void carriesInfantryToAFreePlanetWhileScoringThePresenceObjective() {
        test.place("26", DESTINATION);
        test.units(departure, "quann", test.nekro, UnitType.Infantry, 2);

        TacticalPlan plan = plan().orElseThrow();

        assertThat(plan.kind()).isEqualTo(Kind.EXPAND);
        assertThat(moved(plan, UnitType.Carrier)).isEqualTo(1);
        assertThat(moved(plan, UnitType.Infantry)).isPositive();
        assertThat(plan.landings()).containsKey("lodor");
    }

    // The expansion plan gives up on a system as soon as the enemy has units anywhere in it, here a strong garrison
    // on Starpoint. Nothing stops the carrier landing on New Albion next to it, and the presence objective still sends
    // the carrier, so the move used to go empty.
    @Test
    void landsOnTheFreePlanetOfASystemWhoseOtherPlanetTheEnemyHolds() {
        enemyHolds(twoPlanetSystem(), "starpoint", 4, 1);
        test.units(departure, "quann", test.nekro, UnitType.Infantry, 2);

        TacticalPlan plan = plan().orElseThrow();

        assertThat(moved(plan, UnitType.Carrier)).isEqualTo(1);
        assertThat(moved(plan, UnitType.Infantry)).isPositive();
        assertThat(plan.landings()).containsOnlyKeys("newalbion");
        assertThat(plan.kind()).isEqualTo(Kind.EXPAND);
    }

    // The taken planet is part of the score: the loaded move beats the same move made empty.
    @Test
    void scoresThePlanetItTakes() {
        enemyHolds(twoPlanetSystem(), "starpoint", 4, 1);
        test.units(departure, "quann", test.nekro, UnitType.Infantry, 2);
        double loaded = plan().orElseThrow().score();

        test.nekro.removePlanet("quann");
        double empty = plan().map(TacticalPlan::score).orElse(Double.NEGATIVE_INFINITY);

        assertThat(loaded).isGreaterThan(empty);
    }

    // Fighters in the departure system ride free: a carrier positioning with nothing else to carry takes them along.
    @Test
    void aPositioningCarrierTakesTheFightersAlongToo() {
        enemyHolds(twoPlanetSystem(), "starpoint", 4, 1);
        test.units(departure, "space", test.nekro, UnitType.Fighter, 2);

        TacticalPlan plan = plan().orElseThrow();

        assertThat(moved(plan, UnitType.Carrier)).isEqualTo(1);
        assertThat(moved(plan, UnitType.Fighter)).isEqualTo(2);
        assertThat(plan.landings()).isEmpty();
    }

    // A space dock lets 3 fighters stay without capacity, so they were never stranded and never travelled; with nothing
    // threatening the dock they now ride along.
    @Test
    void fightersThatCouldStayAtADockRideAlongWhenNothingThreatensIt() {
        enemyHolds(twoPlanetSystem(), "starpoint", 4, 1);
        test.units(departure, "quann", test.nekro, UnitType.Spacedock, 1);
        test.units(departure, "space", test.nekro, UnitType.Fighter, 2);

        TacticalPlan plan = plan().orElseThrow();

        assertThat(moved(plan, UnitType.Fighter)).as(plan.toString()).isEqualTo(2);
    }

    // Ground forces come first and the fighters fill what is left of the capacity.
    @Test
    void fightersFillTheCapacityTheGroundForcesLeave() {
        enemyHolds(twoPlanetSystem(), "starpoint", 4, 1);
        test.units(departure, "quann", test.nekro, UnitType.Infantry, 1);
        test.units(departure, "space", test.nekro, UnitType.Fighter, 3);

        TacticalPlan plan = plan().orElseThrow();

        assertThat(moved(plan, UnitType.Infantry)).isEqualTo(1);
        assertThat(moved(plan, UnitType.Fighter)).isEqualTo(3);
        assertThat(plan.landings()).containsOnlyKeys("newalbion");
    }

    // Nothing to carry: the carrier still goes for the objective, and no landing is invented.
    @Test
    void movesTheCarrierAloneWhenThereIsNothingToCarry() {
        enemyHolds(twoPlanetSystem(), "starpoint", 4, 1);

        TacticalPlan plan = plan().orElseThrow();

        assertThat(moved(plan, UnitType.Carrier)).isEqualTo(1);
        assertThat(plan.landings()).isEmpty();
        assertThat(moved(plan, UnitType.Infantry)).isZero();
    }

    // The only planet in the system is held by the enemy with a garrison: not a free planet, so nothing is landed.
    @Test
    void landsNothingOnAPlanetTheEnemyHolds() {
        enemyHolds(test.place("26", DESTINATION), "lodor", 4, 1);
        test.units(departure, "quann", test.nekro, UnitType.Infantry, 2);

        Optional<TacticalPlan> plan = plan();

        assertThat(plan.map(TacticalPlan::landings))
                .hasValueSatisfying(landings -> assertThat(landings).doesNotContainKey("lodor"));
        assertThat(plan.map(found -> moved(found, UnitType.Infantry))).hasValue(0);
    }

    // Enemy ground forces standing on a planet nobody has registered as theirs still make it a bad landing.
    @Test
    void landsNothingOnAPlanetWithEnemyGroundForcesOnIt() {
        Tile tile = twoPlanetSystem();
        enemyHolds(tile, "starpoint", 4, 1);
        test.units(tile, "newalbion", test.sol, UnitType.Infantry, 3);
        test.units(departure, "quann", test.nekro, UnitType.Infantry, 2);

        TacticalPlan plan = plan().orElseThrow();

        assertThat(plan.landings()).isEmpty();
        assertThat(moved(plan, UnitType.Infantry)).isZero();
    }

    // Starpoint is hazardous, so the one place in the hold goes to the mech rather than the infantry.
    @Test
    void landsAMechOnAHazardousPlanetAndKeepsTheInfantryBehind() {
        Tile tile = twoPlanetSystem();
        enemyHolds(tile, "newalbion", 4, 1);
        test.units(departure, "quann", test.nekro, UnitType.Infantry, 2);
        test.units(departure, "quann", test.nekro, UnitType.Mech, 1);

        TacticalPlan plan = plan().orElseThrow();

        assertThat(plan.landings()).containsOnlyKeys("starpoint");
        assertThat(moved(plan, UnitType.Mech)).isEqualTo(1);
        assertThat(moved(plan, UnitType.Infantry)).isZero();
    }

    // Both planets are free, so a mixed force of one infantry and one mech takes one each.
    @Test
    void splitsAMixedForceAcrossTwoFreePlanets() {
        twoPlanetSystem();
        test.units(departure, "quann", test.nekro, UnitType.Infantry, 1);
        test.units(departure, "quann", test.nekro, UnitType.Mech, 1);

        TacticalPlan plan = plan().orElseThrow();

        assertThat(plan.landings()).containsOnlyKeys("starpoint", "newalbion");
        assertThat(moved(plan, UnitType.Mech)).isEqualTo(1);
        assertThat(moved(plan, UnitType.Infantry)).isEqualTo(1);
    }

    private DefenseScenario threatenedHome(int infantry) {
        DefenseScenario board = new DefenseScenario();
        AiTestGame home = board.test;
        Tile target = home.place("27", board.otherNeighbourOfHome());
        home.nekro.addTech("det");
        target.getSpaceUnitHolder().addToken(Mapper.getTokenID(Constants.FRONTIER));
        board.sardakk.addPlanet("starpoint");
        home.units(target, "starpoint", board.sardakk, UnitType.Infantry, 4);
        home.units(board.home, "space", home.nekro, UnitType.Carrier, 1);
        home.units(board.home, "mordaiii", home.nekro, UnitType.Infantry, infantry);
        board.raidingFleet(2, 3, 4);
        return board;
    }

    private TacticalPlan planFrom(DefenseScenario board) {
        return TacticalPlanner.forTarget(board.test.game, board.test.nekro, board.otherNeighbourOfHome())
                .orElseThrow();
    }

    // At home only what the defence model can spare is carried; the rest of the garrison stays on the planet.
    @Test
    void carriesOnlyWhatTheHomeGarrisonCanSpare() {
        DefenseScenario board = threatenedHome(6);
        int spare = TacticalPlanner.movableGroundForces(board.test.game, board.test.nekro, board.home);

        TacticalPlan plan = planFrom(board);

        assertThat(spare).isBetween(1, 5);
        assertThat(plan.landings()).containsOnlyKeys("newalbion");
        assertThat(moved(plan, UnitType.Infantry)).isBetween(1, spare);
    }

    // When the garrison needs everything it has, the carrier goes without cargo rather than strip home.
    @Test
    void takesNoInfantryWhenTheHomeGarrisonNeedsThemAll() {
        DefenseScenario board = threatenedHome(3);

        TacticalPlan plan = planFrom(board);

        assertThat(TacticalPlanner.movableGroundForces(board.test.game, board.test.nekro, board.home))
                .isZero();
        assertThat(moved(plan, UnitType.Infantry)).isZero();
        assertThat(plan.landings()).isEmpty();
    }
}
