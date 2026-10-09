package ti4.ai.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.tactical.ProductionPlanner;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.ai.tactical.TacticalPlanner;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.image.PositionMapper;
import ti4.testUtils.BaseTi4Test;

class ObjectiveValueTest extends BaseTi4Test {

    private static final String EMPTY_SYSTEM = "46";

    private AiTestGame test;
    private Tile home;
    private String neighbour;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setPhaseOfGame("action");
        home = test.nekroHome();
        neighbour = AiTestGame.neighbourOf(AiTestGame.HOME);
    }

    private void reveal(String objective) {
        test.game
                .getRevealedPublicObjectives()
                .put(objective, test.game.getRevealedPublicObjectives().size() + 1);
    }

    private void parkDestroyer(String tileId, String position) {
        Tile parked = test.place(tileId, position);
        test.units(parked, "space", test.nekro, UnitType.Destroyer, 1);
    }

    // Explore Deep Space counts systems without planets that hold the seat's units: parking a destroyer in one is
    // progress worth part of a victory point.
    @Test
    void valuesMovingIntoASystemWithoutPlanetsForExploreDeepSpace() {
        reveal("deep_space");
        test.units(home, "space", test.nekro, UnitType.Destroyer, 1);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.place(EMPTY_SYSTEM, neighbour);
        ObjectiveValue value = new ObjectiveValue(test.game, test.nekro);

        Footprint after = value.before()
                .after(neighbour, List.of(new Footprint.Move(AiTestGame.HOME, UnitType.Destroyer, 1)), List.of());

        assertThat(value.caresAboutPresence()).isTrue();
        assertThat(value.gain(after)).isPositive().isLessThan(ObjectiveValue.VICTORY_POINT_VALUE);
    }

    // Moving the only ship out of one empty system into another changes nothing for the objective.
    @Test
    void givesNothingForSwappingOneEmptySystemForAnother() {
        reveal("deep_space");
        Tile parked = test.place(EMPTY_SYSTEM, neighbour);
        test.units(parked, "space", test.nekro, UnitType.Destroyer, 1);
        String elsewhere = PositionMapper.getAdjacentTilePositions(neighbour).stream()
                .filter(position -> !"x".equals(position) && !AiTestGame.HOME.equals(position))
                .findFirst()
                .orElseThrow();
        test.place("47", elsewhere);
        ObjectiveValue value = new ObjectiveValue(test.game, test.nekro);

        Footprint after = value.before()
                .after(elsewhere, List.of(new Footprint.Move(neighbour, UnitType.Destroyer, 1)), List.of());

        assertThat(value.gain(after)).isZero();
    }

    // Reaching the threshold is worth a full victory point.
    @Test
    void valuesCompletingAndLosingAnObjectiveAsAVictoryPoint() {
        reveal("raise_fleet");
        test.units(home, "space", test.nekro, UnitType.Destroyer, 4);
        Tile staging = test.place(EMPTY_SYSTEM, neighbour);
        test.units(staging, "space", test.nekro, UnitType.Cruiser, 1);
        ObjectiveValue value = new ObjectiveValue(test.game, test.nekro);

        Footprint joined = value.before()
                .after(AiTestGame.HOME, List.of(new Footprint.Move(neighbour, UnitType.Cruiser, 1)), List.of());

        assertThat(value.gain(joined)).isEqualTo(ObjectiveValue.VICTORY_POINT_VALUE);
    }

    @Test
    void chargesAVictoryPointForBreakingUpAQualifyingFleet() {
        reveal("raise_fleet");
        test.units(home, "space", test.nekro, UnitType.Destroyer, 5);
        test.place(EMPTY_SYSTEM, neighbour);
        ObjectiveValue value = new ObjectiveValue(test.game, test.nekro);

        Footprint split = value.before()
                .after(neighbour, List.of(new Footprint.Move(AiTestGame.HOME, UnitType.Destroyer, 1)), List.of());

        assertThat(value.gain(split)).isEqualTo(-ObjectiveValue.VICTORY_POINT_VALUE);
    }

    // The planner turns presence objectives into moves: with nothing else to do, it parks a destroyer in the empty
    // neighbouring system.
    @Test
    void plansAPositionMoveForAPresenceObjective() {
        reveal("deep_space");
        test.units(home, "space", test.nekro, UnitType.Destroyer, 1);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.place(EMPTY_SYSTEM, neighbour);

        Optional<TacticalPlan> plan = TacticalPlanner.best(test.game, test.nekro);

        assertThat(plan).isPresent();
        assertThat(plan.get().kind()).isEqualTo(Kind.POSITION);
        assertThat(plan.get().target()).isEqualTo(neighbour);
        assertThat(plan.get().moves())
                .containsExactly(new TacticalPlan.UnitMove(AiTestGame.HOME, "space", UnitType.Destroyer, 1));
    }

    // Cut Supply Lines needs a ship in the same system as another player's space dock. A dock with no ships guarding
    // it can be reached by a plain move, and that move completes the secret.
    @Test
    void movesBesideAnUnguardedSpaceDockForCutSupplyLines() {
        test.nekro.setSecret("csl");
        test.units(home, "space", test.nekro, UnitType.Destroyer, 1);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        Tile docked = test.place("19", neighbour);
        test.sol.addPlanet("wellon");
        test.units(docked, "wellon", test.sol, UnitType.Spacedock, 1);
        ObjectiveValue value = new ObjectiveValue(test.game, test.nekro);

        Footprint after = value.before()
                .after(neighbour, List.of(new Footprint.Move(AiTestGame.HOME, UnitType.Destroyer, 1)), List.of());

        assertThat(value.gain(after)).isEqualTo(ObjectiveValue.VICTORY_POINT_VALUE);
        assertThat(TacticalPlanner.best(test.game, test.nekro).map(TacticalPlan::target))
                .contains(neighbour);
    }

    // Discover Lost Outposts counts controlled planets with attachments, so taking such a planet is progress.
    @Test
    void valuesTakingAPlanetWithAnAttachment() {
        reveal("lost_outposts");
        Tile tile = test.place("19", neighbour);
        tile.getUnitHolders().get("wellon").addToken("attachment_cybernetic.png");
        ObjectiveValue value = new ObjectiveValue(test.game, test.nekro);

        Footprint after = value.before().after(neighbour, List.of(), List.of("wellon"));

        assertThat(value.gain(after)).isPositive().isLessThan(ObjectiveValue.VICTORY_POINT_VALUE);
    }

    // Foster Cohesion needs the seat to neighbour every other player. Moving a ship next to the one player it doesn't
    // yet touch completes it.
    @Test
    void valuesBecomingNeighboursForFosterCohesion() {
        test.nekro.setSecret("fc");
        test.units(home, "space", test.nekro, UnitType.Destroyer, 1);
        test.place(EMPTY_SYSTEM, neighbour);
        List<String> nearHome = PositionMapper.getAdjacentTilePositions(AiTestGame.HOME);
        String beyond = PositionMapper.getAdjacentTilePositions(neighbour).stream()
                .filter(position -> !"x".equals(position) && !AiTestGame.HOME.equals(position))
                .filter(position -> !nearHome.contains(position))
                .findFirst()
                .orElseThrow();
        Tile solSystem = test.place("47", beyond);
        test.units(solSystem, "space", test.sol, UnitType.Destroyer, 1);
        ObjectiveValue value = new ObjectiveValue(test.game, test.nekro);

        Footprint after = value.before()
                .after(neighbour, List.of(new Footprint.Move(AiTestGame.HOME, UnitType.Destroyer, 1)), List.of());

        assertThat(value.progress("fc", value.before())).isZero();
        assertThat(value.gain(after)).isEqualTo(ObjectiveValue.VICTORY_POINT_VALUE);
    }

    // Raise a Fleet needs five non-fighter ships in one system: with five fleet tokens, the home dock builds cheap
    // destroyers next to the ships already there before spending production on anything else.
    @Test
    void buildsDestroyersAtADockToRaiseAFleet() {
        reveal("raise_fleet");
        test.nekro.setFleetCC(5);
        test.nekro.setTg(10);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 2);

        ProductionPlanner.BuildPlan plan = ProductionPlanner.plan(test.game, test.nekro, home);

        assertThat(plan.units(UnitType.Destroyer)).isEqualTo(3);
    }

    // Patrol Vast Territories is worth two points and needs units in five systems without planets. Holding two
    // already (ring-1 positions, never adjacent to the ring-3 home), the seat is three steps from done, which the
    // planner can close within a round or two: one more planetless system is worth more than half a victory point,
    // yet still less than a point it would score for certain.
    @Test
    void valuesAStepTowardAStageTwoObjectiveWithinReachAboveHalfAPoint() {
        reveal("vast_territories");
        test.units(home, "space", test.nekro, UnitType.Destroyer, 1);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.place(EMPTY_SYSTEM, neighbour);
        parkDestroyer("47", "104");
        parkDestroyer("48", "105");
        ObjectiveValue value = new ObjectiveValue(test.game, test.nekro);

        Footprint after = value.before()
                .after(neighbour, List.of(new Footprint.Move(AiTestGame.HOME, UnitType.Destroyer, 1)), List.of());

        assertThat(value.gain(after))
                .isGreaterThan(ObjectiveValue.VICTORY_POINT_VALUE / 2)
                .isLessThan(ObjectiveValue.VICTORY_POINT_VALUE);
    }

    // From nothing, the same objective is five steps away and out of reach for now: the first planetless system keeps
    // the ordinary partial share, under a quarter of a point.
    @Test
    void keepsTheOrdinaryShareForAStageTwoObjectiveOutOfReach() {
        reveal("vast_territories");
        test.units(home, "space", test.nekro, UnitType.Destroyer, 1);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.place(EMPTY_SYSTEM, neighbour);
        ObjectiveValue value = new ObjectiveValue(test.game, test.nekro);

        Footprint after = value.before()
                .after(neighbour, List.of(new Footprint.Move(AiTestGame.HOME, UnitType.Destroyer, 1)), List.of());

        assertThat(value.gain(after)).isPositive().isLessThan(ObjectiveValue.VICTORY_POINT_VALUE / 4);
    }

    // Progress within reach is as costly to give up as it was valuable to gain: pulling the destroyer out of one of
    // three planetless systems costs more than half a point, so the planner keeps it parked.
    @Test
    void chargesMoreThanHalfAPointForGivingUpProgressWithinReach() {
        reveal("vast_territories");
        parkDestroyer(EMPTY_SYSTEM, neighbour);
        parkDestroyer("47", "104");
        parkDestroyer("48", "105");
        ObjectiveValue value = new ObjectiveValue(test.game, test.nekro);

        Footprint after = value.before()
                .after(AiTestGame.HOME, List.of(new Footprint.Move(neighbour, UnitType.Destroyer, 1)), List.of());

        assertThat(value.gain(after)).isLessThan(-ObjectiveValue.VICTORY_POINT_VALUE / 2);
    }

    // Stage I objectives keep the ordinary share: a second planetless system toward Explore Deep Space is still worth
    // under a third of a point.
    @Test
    void keepsTheOrdinaryShareForAStageOneObjective() {
        reveal("deep_space");
        test.units(home, "space", test.nekro, UnitType.Destroyer, 1);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.place(EMPTY_SYSTEM, neighbour);
        parkDestroyer("47", "104");
        ObjectiveValue value = new ObjectiveValue(test.game, test.nekro);

        Footprint after = value.before()
                .after(neighbour, List.of(new Footprint.Move(AiTestGame.HOME, UnitType.Destroyer, 1)), List.of());

        assertThat(value.gain(after)).isPositive().isLessThan(ObjectiveValue.VICTORY_POINT_VALUE / 3);
    }
}
