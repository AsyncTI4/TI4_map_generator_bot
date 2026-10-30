package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.Territory;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

// Expanding and attacking lean towards the seat's own area: its slice and the lanes towards Mecatol Rex score extra
// per planet, a rival's area costs something early on.
class TerritoryPlanningTest extends BaseTi4Test {

    private static final String RIVAL_HOME = "304";
    private static final String OWN_SLICE = "302";
    private static final String RIVAL_AREA = "303";
    private static final String CARRIER_SPOT = "202";
    private static final String EMPTY_SYSTEM = "46";
    private static final String THIBAH = "21";
    private static final String TARMANN = "22";
    private static final String WELLON = "19";
    private static final String SAUDOR = "23";
    private static final String LODOR = "26";

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        test.place("01", RIVAL_HOME);
        test.sol.addPlanet("jord");
    }

    private void carrierWithOneInfantryAt(String position) {
        Tile origin = test.place(EMPTY_SYSTEM, position);
        test.units(origin, "space", test.nekro, UnitType.Carrier, 1);
        test.units(origin, "space", test.nekro, UnitType.Infantry, 1);
    }

    private Optional<TacticalPlan> expansionTo(String position) {
        return TacticalPlanner.forTarget(test.game, test.nekro, position).filter(plan -> plan.kind() == Kind.EXPAND);
    }

    private static double planetValueIn(Tile tile) {
        return tile.getPlanetUnitHolders().stream()
                .mapToDouble(BoardView::planetValue)
                .sum();
    }

    // Two free industrial planets of the same value (one resource, one influence and a technology specialty), one in
    // the seat's own slice and one on the rival's side of the map, both a step from the same carrier. The seat takes
    // the one in its own slice.
    @Test
    void expandsIntoItsOwnSliceRatherThanTheRivalsArea() {
        carrierWithOneInfantryAt(CARRIER_SPOT);
        Tile slice = test.place(THIBAH, OWN_SLICE);
        Tile foreign = test.place(TARMANN, RIVAL_AREA);
        assertThat(planetValueIn(slice)).isEqualTo(planetValueIn(foreign));
        Territory territory = Territory.of(test.game, test.nekro);
        assertThat(territory.zone(slice)).isEqualTo(Territory.Zone.SLICE);
        assertThat(territory.zone(foreign)).isEqualTo(Territory.Zone.FOREIGN);

        List<TacticalPlan> expansions = TacticalPlanner.ranked(test.game, test.nekro).stream()
                .filter(plan -> plan.kind() == Kind.EXPAND)
                .toList();

        assertThat(expansions).isNotEmpty();
        assertThat(expansions.getFirst().target()).isEqualTo(OWN_SLICE);
        assertThat(expansionTo(OWN_SLICE).orElseThrow().score())
                .isGreaterThan(expansionTo(RIVAL_AREA).orElseThrow().score());
    }

    @Test
    void theRivalsAreaCostsLessAsTheGameGoesOn() {
        carrierWithOneInfantryAt(CARRIER_SPOT);
        test.place(TARMANN, RIVAL_AREA);
        test.game.setRound(2);
        double early = expansionTo(RIVAL_AREA).orElseThrow().score();

        test.game.setRound(8);
        double late = expansionTo(RIVAL_AREA).orElseThrow().score();

        assertThat(late).isGreaterThan(early);
    }

    // The lane towards Mecatol Rex outweighs a border planet worth about half a point more.
    @Test
    void aFreePlanetOnTheLaneBeatsABorderPlanetWorthMore() {
        test.place("18", "000");
        carrierWithOneInfantryAt("201");
        Tile lane = test.place(WELLON, "101");
        Tile border = test.place(SAUDOR, "202");
        Territory territory = Territory.of(test.game, test.nekro);
        assertThat(territory.zone(lane)).isEqualTo(Territory.Zone.LANE);
        assertThat(territory.zone(border)).isEqualTo(Territory.Zone.BORDER);
        assertThat(planetValueIn(border) - planetValueIn(lane)).isBetween(0.4, 0.8);

        assertThat(expansionTo("101").orElseThrow().score())
                .isGreaterThan(expansionTo("202").orElseThrow().score());
    }

    private void fleetAtHome() {
        test.units(test.game.getTileByPosition(AiTestGame.HOME), "space", test.nekro, UnitType.Carrier, 1);
        test.units(test.game.getTileByPosition(AiTestGame.HOME), "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(test.game.getTileByPosition(AiTestGame.HOME), "mordaiii", test.nekro, UnitType.Infantry, 4);
    }

    private StrikeValue breakdown(String position) {
        return TacticalPlanner.explainAttack(test.game, test.nekro, position).orElseThrow();
    }

    private void rivalHoldsLodorAt(String position) {
        Tile lodor = test.place(LODOR, position);
        test.sol.addPlanet("lodor");
        test.units(lodor, "lodor", test.sol, UnitType.Infantry, 1);
    }

    // Sol's Lodor next to the seat's home is taken for the slice as well as for its own worth.
    @Test
    void anAttackOnAPlanetInTheSeatsSliceGetsTheBonus() {
        fleetAtHome();
        String nearHome = AiTestGame.neighbourOf(AiTestGame.HOME);
        rivalHoldsLodorAt(nearHome);

        StrikeValue inSlice = breakdown(nearHome);

        assertThat(inSlice.planets()).isGreaterThan(0);
        assertThat(inSlice.territory()).isGreaterThan(0);
    }

    // The same planet beside Sol's home, on its side of the map, adds nothing for the territory.
    @Test
    void anAttackOnAPlanetInTheRivalsAreaGetsNoBonus() {
        String besideRival = neighbourOf(RIVAL_HOME, AiTestGame.HOME);
        rivalHoldsLodorAt(besideRival);
        Tile launchTile = test.place(EMPTY_SYSTEM, neighbourOf(besideRival, RIVAL_HOME, AiTestGame.HOME));
        test.units(launchTile, "space", test.nekro, UnitType.Carrier, 1);
        test.units(launchTile, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(launchTile, "space", test.nekro, UnitType.Infantry, 4);
        assertThat(Territory.of(test.game, test.nekro).zone(test.game.getTileByPosition(besideRival)))
                .isEqualTo(Territory.Zone.FOREIGN);

        StrikeValue inRivalsArea = breakdown(besideRival);

        assertThat(inRivalsArea.planets()).isGreaterThan(0);
        assertThat(inRivalsArea.territory()).isZero();
    }

    private String neighbourOf(String position, String... excluded) {
        List<String> skip = List.of(excluded);
        return MapPositionMapper.getAdjacentTilePositions(position).stream()
                .filter(candidate -> !"x".equals(candidate)
                        && !skip.contains(candidate)
                        && !MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME)
                                .contains(candidate)
                        && test.game.getTileByPosition(candidate) == null)
                .findFirst()
                .orElseThrow();
    }
}
