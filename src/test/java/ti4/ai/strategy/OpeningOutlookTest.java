package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.strategy.OpeningOutlook.Boost;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

// Round 1 is about taking as much of the slice as possible. The opening outlook plays the round's expansions out from
// home with and without each strategy card's help, and credits a card with the planets only it makes reachable.
class OpeningOutlookTest extends BaseTi4Test {

    private AiTestGame test;
    private Tile home;
    private String neighbour;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setRound(1);
        home = test.nekroHome();
        neighbour = AiTestGame.neighbourOf(AiTestGame.HOME);
        test.nekro.setTacticalCC(3);
    }

    // One carrier and two spare infantry can take only one of the two free two-planet systems next to home. Producing a
    // carrier and infantry at home before anyone moves (Thunder's Edge Construction) takes the other one as well.
    @Test
    void constructionHelpsWhenShortOfCarriersAndInfantry() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 3);
        test.place("27", neighbour);
        test.place("28", otherNeighbour());

        assertThat(OpeningOutlook.gain(test.game, test.nekro, Boost.HOME_PRODUCTION))
                .isGreaterThan(0);
    }

    // With two carriers and plenty of infantry the same two systems are taken anyway, so production adds nothing.
    @Test
    void constructionAddsNothingWhenTheUnitsAreAlreadyThere() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 2);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 6);
        test.place("27", neighbour);
        test.place("28", otherNeighbour());

        assertThat(OpeningOutlook.gain(test.game, test.nekro, Boost.HOME_PRODUCTION))
                .isZero();
    }

    // Only one system with planets is in reach; the rest of the slice lies past it. Warfare's extra action lets the
    // carrier that took it carry its spare infantry on to the next system.
    @Test
    void warfareReachesPastTheOnlySystemInReach() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 5);
        test.place("26", neighbour);
        test.place("27", beyond(neighbour));

        assertThat(OpeningOutlook.gain(test.game, test.nekro, Boost.EXTRA_ACTION))
                .isGreaterThan(0);
    }

    // The second carrier can reach the system two away only with Gravity Drive, which it can research once it holds a
    // blue technology.
    @Test
    void gravityDriveReachesASecondSystemForTheSecondCarrier() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 2);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 5);
        test.place("26", neighbour);
        test.place("27", beyond(neighbour));
        assertThat(OpeningOutlook.gain(test.game, test.nekro, Boost.GRAVITY_DRIVE))
                .isZero();

        test.nekro.addTech("amd");

        assertThat(OpeningOutlook.gain(test.game, test.nekro, Boost.GRAVITY_DRIVE))
                .isGreaterThan(0);
    }

    // After round 1 the opening is over.
    @Test
    void onlyCountsInRoundOne() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 3);
        test.place("27", neighbour);
        test.place("28", otherNeighbour());
        test.game.setRound(2);

        assertThat(OpeningOutlook.gain(test.game, test.nekro, Boost.HOME_PRODUCTION))
                .isZero();
    }

    // Nekro's real start: a dreadnought, a carrier and a cruiser fill all three fleet tokens, and two infantry wait
    // on Mordai II. Two free systems lie next to home. Producing first finds no fleet room for a second carrier, so the
    // carrier moves out first and Construction then builds the second carrier and its infantry for the other system.
    @Test
    void movesOutFirstWhenTwoSystemsAreInReach() {
        nekroStartingFleet(UnitType.Cruiser);
        test.place("27", neighbour);
        test.place("28", otherNeighbour());

        OpeningOutlook.Production production =
                OpeningOutlook.bestProduction(test.game, test.nekro).orElseThrow();

        assertThat(production.order()).isEqualTo(OpeningOutlook.Order.MOVE_FIRST);
        assertThat(production.overSupply()).isFalse();
        assertThat(OpeningOutlook.producesBeforeMoving(test.game, test.nekro)).isFalse();
        assertThat(OpeningOutlook.gain(test.game, test.nekro, Boost.HOME_PRODUCTION))
                .isGreaterThan(0);
    }

    // Only one system with planets is in reach and the rest of the slice lies past it. A second carrier would have
    // nowhere to go, so Construction comes first and builds infantry: the carrier lands two and carries two more
    // forward for the system past it next round.
    @Test
    void producesFirstWhenOnlyOneSystemIsInReach() {
        nekroStartingFleet(UnitType.Cruiser);
        test.place("27", neighbour);
        test.place("28", beyond(neighbour));

        OpeningOutlook.Production production =
                OpeningOutlook.bestProduction(test.game, test.nekro).orElseThrow();

        assertThat(production.order()).isEqualTo(OpeningOutlook.Order.PRODUCE_FIRST);
        assertThat(production.overSupply()).isFalse();
        assertThat(OpeningOutlook.gain(test.game, test.nekro, Boost.HOME_PRODUCTION))
                .isGreaterThan(0);
    }

    // The single system in reach opens onto two more. With trade goods to pay for it, a carrier built past the fleet
    // limit (the destroyer is removed to make room) lets two loaded carriers move forward together and take both
    // systems next round, which is worth more than the destroyer.
    @Test
    void overSuppliesACarrierWhenTwoLoadedCarriersForwardOpenTheNextRound() {
        nekroStartingFleet(UnitType.Destroyer);
        test.nekro.setTg(3);
        test.place("27", neighbour);
        List<String> past = allBeyond(neighbour);
        test.place("28", past.get(0));
        test.place("30", past.get(1));

        OpeningOutlook.Production production =
                OpeningOutlook.bestProduction(test.game, test.nekro).orElseThrow();

        assertThat(production.order()).isEqualTo(OpeningOutlook.Order.PRODUCE_FIRST);
        assertThat(production.overSupply()).isTrue();
        assertThat(OpeningOutlook.overSupplyCarriers(test.game, test.nekro, home))
                .isEqualTo(1);
    }

    // Without a ship cheaper than a carrier to give up, building past the fleet limit is never planned.
    @Test
    void neverOverSuppliesWithoutAShipCheaperThanACarrier() {
        test.nekro.setFleetCC(1);
        test.nekro.setTg(3);
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 2);
        test.place("27", neighbour);
        List<String> past = allBeyond(neighbour);
        test.place("28", past.get(0));
        test.place("30", past.get(1));

        assertThat(OpeningOutlook.overSupplyCarriers(test.game, test.nekro, home))
                .isZero();
    }

    // Another seat holds Warfare. With one system in reach, following its secondary before the carrier leaves fills
    // the carrier with fresh infantry, worth spending a turn on a cheap move; with two systems in reach the carrier
    // should go at once and the home build can follow it.
    @Test
    void holdsTheCarriersForTheWarfareBuildOnlyWhenOneSystemIsInReach() {
        nekroStartingFleet(UnitType.Destroyer);
        test.place("27", neighbour);
        test.place("28", beyond(neighbour));
        assertThat(OpeningOutlook.holdsCarriersForHomeBuild(test.game, test.nekro))
                .isTrue();

        test.place("29", otherNeighbour());

        assertThat(OpeningOutlook.holdsCarriersForHomeBuild(test.game, test.nekro))
                .isFalse();
    }

    private void nekroStartingFleet(UnitType lightShip) {
        test.nekro.setFleetCC(3);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, lightShip, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 2);
    }

    private static List<String> allBeyond(String position) {
        List<String> nearHome = MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME);
        return MapPositionMapper.getAdjacentTilePositions(position).stream()
                .filter(candidate ->
                        !"x".equals(candidate) && !AiTestGame.HOME.equals(candidate) && !nearHome.contains(candidate))
                .toList();
    }

    private String otherNeighbour() {
        return MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME).stream()
                .filter(position -> !"x".equals(position) && !position.equals(neighbour))
                .findFirst()
                .orElseThrow();
    }

    private static String beyond(String position) {
        List<String> nearHome = MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME);
        return MapPositionMapper.getAdjacentTilePositions(position).stream()
                .filter(candidate ->
                        !"x".equals(candidate) && !AiTestGame.HOME.equals(candidate) && !nearHome.contains(candidate))
                .findFirst()
                .orElseThrow();
    }
}
