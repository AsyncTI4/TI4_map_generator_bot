package ti4.ai.explore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.ValueScale;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
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
        return MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME).stream()
                .filter(candidate ->
                        !"x".equals(candidate) && !site.getPosition().equals(candidate))
                .findFirst()
                .orElseThrow();
    }

    private String besideSite(int skipped) {
        return MapPositionMapper.getAdjacentTilePositions(site.getPosition()).stream()
                .filter(candidate -> !"x".equals(candidate)
                        && !AiTestGame.HOME.equals(candidate)
                        && test.game.getTileByPosition(candidate) == null)
                .skip(skipped)
                .findFirst()
                .orElseThrow();
    }

    private String besideSite() {
        return besideSite(0);
    }

    private void carrierAtSite() {
        test.units(site, "space", test.nekro, UnitType.Carrier, 1);
    }

    private double need() {
        return outlook().expansionNeed(site.getUnitHolderFromPlanet("tequran"));
    }

    // Half the value of the best planet left to claim (Lodor, 3.2) is the price of an infantry that is needed to take
    // it, but only if a ship of the seat can reach it: without ships nothing can.
    @Test
    void anInfantryIsNeededOnlyForAPlanetAShipCanReach() {
        test.place("26", besideSite());
        assertThat(need()).isZero();

        carrierAtSite();
        assertThat(need()).isCloseTo(0.5 * ValueScale.spendable(3, 1), within(EXACT));
    }

    // A transport that can pick up other ground forces (here at home) leaves the infantry free.
    @Test
    void anInfantryIsNotNeededWhenOthersCanTakeThePlanet() {
        test.place("26", besideSite());
        carrierAtSite();
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 1);

        assertThat(need()).isZero();
    }

    // A planet somebody holds, or that is guarded by another player's ground forces, is not up for claiming.
    @Test
    void aHeldPlanetIsNotLeftToClaim() {
        Tile lodor = test.place("26", besideSite());
        carrierAtSite();
        test.sol.addPlanet("lodor");
        assertThat(need()).isZero();

        test.sol.removePlanet("lodor");
        test.units(lodor, "lodor", test.sol, UnitType.Infantry, 1);
        assertThat(need()).isZero();
    }

    // An infantry costs 0.7. It costs the claim it is needed for as well, and a small share of the planet's stake when
    // it is the last garrison and a Sol ship can reach the planet.
    @Test
    void anInfantryCostsMoreWhenNeededAndWhenItIsTheLastGarrisonUnderThreat() {
        ExploreSite quiet = ExploreSite.onBoard(test.game, test.nekro, "tequran", outlook());
        assertThat(quiet.infantryCost()).isCloseTo(ExploreValues.INFANTRY_UNIT, within(EXACT));

        String solSpot = MapPositionMapper.getAdjacentTilePositions(site.getPosition()).stream()
                .filter(candidate -> !"x".equals(candidate) && test.game.getTileByPosition(candidate) == null)
                .findFirst()
                .orElseThrow();
        test.units(test.place("25", solSpot), "space", test.sol, UnitType.Destroyer, 1);
        ExploreSite threatened = ExploreSite.onBoard(test.game, test.nekro, "tequran", outlook());
        double stake = outlook().planetStake(site.getUnitHolderFromPlanet("tequran"));
        assertThat(threatened.infantryCost())
                .isCloseTo(ExploreValues.INFANTRY_UNIT + ExploreValues.LAST_FORCE_RISK_SHARE * stake, within(EXACT));

        test.place("26", besideSite());
        carrierAtSite();
        ExploreSite needed = ExploreSite.onBoard(test.game, test.nekro, "tequran", outlook());
        assertThat(needed.infantryCost()).isGreaterThan(ExploreValues.INFANTRY_UNIT + 1);
    }

    // The carrier is unlocked next round with 4 infantry in its system, and every system in its reach has one planet.
    // One activation claims one system, so 4 infantry are more than it can use and the one on Tequran is spare.
    @Test
    void anInfantryIsSpareWhenTheTransportHasMoreThanItCanLand() {
        carrierAtSite();
        test.units(site, "tequran", test.nekro, UnitType.Infantry, 2);
        test.units(site, "torkan", test.nekro, UnitType.Infantry, 1);
        test.place("26", besideSite());
        test.place("25", besideSite());

        assertThat(need()).isZero();
        ExploreSite tequran = ExploreSite.onBoard(test.game, test.nekro, "tequran", outlook());
        assertThat(tequran.infantryCost()).isCloseTo(ExploreValues.INFANTRY_UNIT, within(EXACT));
    }

    // With one infantry beside its only carrier and a system of two free planets in reach, the infantry is needed:
    // without it neither planet can be taken, and both count as the claim that goes unmet.
    @Test
    void anInfantryIsNeededWhenTheClaimWouldGoUnmet() {
        carrierAtSite();
        Tile twoPlanets = test.place("29", besideSite());
        double value = twoPlanets.getPlanetUnitHolders().stream()
                .mapToDouble(BoardView::planetValue)
                .sum();

        assertThat(need()).isCloseTo(ExploreValues.EXPANSION_SHARE * value, within(EXACT));
    }

    // With a second infantry the larger planet is still taken, and only the smaller one is left unmet.
    @Test
    void onlyThePlanetsWithoutAnInfantryAreCharged() {
        carrierAtSite();
        test.units(site, "tequran", test.nekro, UnitType.Infantry, 1);
        Tile twoPlanets = test.place("29", besideSite());
        double smaller = twoPlanets.getPlanetUnitHolders().stream()
                .mapToDouble(BoardView::planetValue)
                .min()
                .orElseThrow();

        assertThat(need()).isCloseTo(ExploreValues.EXPANSION_SHARE * smaller, within(EXACT));
    }

    // Each activation takes a command token, so a seat without tactic tokens claims nothing next round either.
    @Test
    void noInfantryIsNeededWithoutTacticTokens() {
        carrierAtSite();
        test.place("26", besideSite());
        test.nekro.setTacticalCC(0);

        assertThat(need()).isZero();
    }

    // An infantry a carrier cannot pick up on its way to the claim (it moves 1, and the infantry is off its path) is
    // not counted on for it.
    @Test
    void anInfantryOutOfTheTransportsReachIsNotNeeded() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.place("26", otherNeighbourOfHome());

        assertThat(need()).isZero();
    }

    // Readying a planet is worth what it will be spent on before it would ready anyway: here the build its resources
    // add at the home dock. With no tactic token to activate the dock it is worth less than an infantry (only the
    // Thunder's Edge
    // expedition keeps a little), and once the AI has passed nothing is left to spend it on.
    @Test
    void aReadiedPlanetIsWorthWhatItWillBeSpentOn() {
        test.nekro.exhaustPlanet("tequran");
        ExploreSite tequran = ExploreSite.onBoard(test.game, test.nekro, "tequran", outlook());
        assertThat(CardValue.readyValue(tequran)).isGreaterThan(ExploreValues.INFANTRY_UNIT);

        test.nekro.setTacticalCC(0);
        ExploreSite withoutTactic = ExploreSite.onBoard(test.game, test.nekro, "tequran", outlook());
        assertThat(CardValue.readyValue(withoutTactic)).isLessThan(ExploreValues.INFANTRY_UNIT);
    }

    @Test
    void aReadiedPlanetIsWorthNothingOnceTheAiHasPassed() {
        test.nekro.exhaustPlanet("tequran");
        test.nekro.setPassed(true);
        ExploreSite afterPassing = ExploreSite.onBoard(test.game, test.nekro, "tequran", outlook());
        assertThat(CardValue.readyValue(afterPassing)).isZero();
    }

    // A planet that completes a spend objective is worth most of the point, whether or not the AI has passed.
    @Test
    void aReadiedPlanetThatCompletesASpendObjectiveIsWorthMostOfThePoint() {
        test.nekro.getPlanets().forEach(test.nekro::exhaustPlanet);
        test.nekro.refreshPlanet("mordaiii");
        test.nekro.setTg(2);
        test.nekro.setTacticalCC(0);
        test.nekro.setPassed(true);
        test.game.getRevealedPublicObjectives().put("monument", 1);

        ExploreSite tequran = ExploreSite.onBoard(test.game, test.nekro, "tequran", outlook());
        assertThat(CardValue.readyValue(tequran)).isGreaterThan(5);
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
