package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.strategy.OpeningOutlook.Boost;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.image.PositionMapper;
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

    private String otherNeighbour() {
        return PositionMapper.getAdjacentTilePositions(AiTestGame.HOME).stream()
                .filter(position -> !"x".equals(position) && !position.equals(neighbour))
                .findFirst()
                .orElseThrow();
    }

    private static String beyond(String position) {
        List<String> nearHome = PositionMapper.getAdjacentTilePositions(AiTestGame.HOME);
        return PositionMapper.getAdjacentTilePositions(position).stream()
                .filter(candidate ->
                        !"x".equals(candidate) && !AiTestGame.HOME.equals(candidate) && !nearHome.contains(candidate))
                .findFirst()
                .orElseThrow();
    }
}
