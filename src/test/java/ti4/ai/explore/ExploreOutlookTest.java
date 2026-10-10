package ti4.ai.explore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.image.PositionMapper;
import ti4.testUtils.BaseTi4Test;

// What the AI knows about the rest of its round when it weighs an exploration card: whether a planet is left for its
// infantry to claim, whether it will still spend, and whether it could pay for Freelancers.
class ExploreOutlookTest extends BaseTi4Test {

    private static final double EXACT = 1e-9;

    private AiTestGame test;
    private Tile home;
    private Tile site;

    // Nekro holds Tequran (a hazardous 2/0 planet) and Torkan, the planets of the system next to its home, with an
    // infantry on Tequran.
    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        home = test.nekroHome();
        site = test.place("28", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.nekro.addPlanet("tequran");
        test.nekro.addPlanet("torkan");
        test.units(site, "tequran", test.nekro, UnitType.Infantry, 1);
        test.aiIsActive("action");
    }

    private ExploreOutlook outlook() {
        return new ExploreOutlook(test.game, test.nekro);
    }

    private String otherNeighbourOfHome() {
        return PositionMapper.getAdjacentTilePositions(AiTestGame.HOME).stream()
                .filter(candidate ->
                        !"x".equals(candidate) && !site.getPosition().equals(candidate))
                .findFirst()
                .orElseThrow();
    }

    // A planet counts as left to claim only if a ship of the seat can reach its system: without ships nothing can.
    @Test
    void aFreePlanetCountsOnlyWhenAShipCanReachIt() {
        test.place("26", otherNeighbourOfHome());
        assertThat(outlook().claimablePlanetsLeft()).isFalse();

        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        assertThat(outlook().claimablePlanetsLeft()).isTrue();
    }

    // A planet somebody holds, or that is guarded by another player's ground forces, is not up for claiming.
    @Test
    void aHeldPlanetIsNotLeftToClaim() {
        Tile lodor = test.place("26", otherNeighbourOfHome());
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.sol.addPlanet("lodor");
        assertThat(outlook().claimablePlanetsLeft()).isFalse();

        test.sol.removePlanet("lodor");
        test.units(lodor, "lodor", test.sol, UnitType.Infantry, 1);
        assertThat(outlook().claimablePlanetsLeft()).isFalse();
    }

    // An infantry that could still take a planet is worth half a resource more; when a Sol ship can reach the planet
    // and the infantry is its only garrison, the planet's value is at stake as well.
    @Test
    void anInfantryCostsMoreWhileAPlanetIsLeftToClaimAndWhenItIsTheLastGarrison() {
        ExploreSite quiet = ExploreSite.onBoard(test.game, test.nekro, "tequran", outlook());
        assertThat(quiet.infantryCost()).isCloseTo(ExploreValues.INFANTRY_UNIT, within(EXACT));

        test.place("26", otherNeighbourOfHome());
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        ExploreSite claimable = ExploreSite.onBoard(test.game, test.nekro, "tequran", outlook());
        assertThat(claimable.infantryCost())
                .isCloseTo(ExploreValues.INFANTRY_UNIT + ExploreValues.CLAIMABLE_PLANET_PREMIUM, within(EXACT));

        String solSpot = PositionMapper.getAdjacentTilePositions(site.getPosition()).stream()
                .filter(candidate -> !"x".equals(candidate) && test.game.getTileByPosition(candidate) == null)
                .findFirst()
                .orElseThrow();
        test.units(test.place("25", solSpot), "space", test.sol, UnitType.Destroyer, 1);
        ExploreSite threatened = ExploreSite.onBoard(test.game, test.nekro, "tequran", outlook());
        assertThat(threatened.infantryCost()).isGreaterThan(claimable.infantryCost() + 2);
    }

    // Readying a planet is worth its full value while the AI still has a dock with something to buy, and 0.3 of it
    // once it has passed.
    @Test
    void aReadiedPlanetIsWorthLessOnceTheAiHasPassed() {
        test.nekro.exhaustPlanet("tequran");
        ExploreSite tequran = ExploreSite.onBoard(test.game, test.nekro, "tequran", outlook());
        assertThat(CardValue.readyValue(tequran)).isCloseTo(2.0, within(EXACT));

        test.nekro.setPassed(true);
        ExploreSite afterPassing = ExploreSite.onBoard(test.game, test.nekro, "tequran", outlook());
        assertThat(CardValue.readyValue(afterPassing)).isCloseTo(0.6, within(EXACT));
    }

    // Freelancers is worth having when the seat could pay 2 resources of influence or resources for a unit.
    @Test
    void freelancersNeedsSomethingToPayWith() {
        assertThat(outlook().canFundFreelancers()).isTrue();

        test.nekro.getPlanets().forEach(test.nekro::exhaustPlanet);
        assertThat(outlook().canFundFreelancers()).isFalse();

        test.nekro.setTg(2);
        assertThat(outlook().canFundFreelancers()).isTrue();
    }
}
