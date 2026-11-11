package ti4.ai.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.OptionalDouble;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

class ExpertPlanetValuesTest extends BaseTi4Test {

    private static final String LODOR = "lodor";
    private static final String MEHAR_XULL = "meharxull";
    private static final String WELLON = "wellon";

    private AiTestGame test;
    private Player nekro;
    private Player sol;
    private Player hacan;
    private Player jolnar;
    private Tile lodorTile;
    private Tile meharXullTile;

    // Four seats with the expert value table on (its default): Nekro and Sol at home, Hacan and Jol-Nar seated with
    // nothing on the board. Lodor is cultural, Mehar Xull hazardous and Wellon industrial; nobody holds them yet.
    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        nekro = test.nekro;
        sol = test.sol;
        hacan = test.addSeat("300000000000000003", "hacan", "yellow");
        jolnar = test.addSeat("300000000000000004", "jolnar", "purple");
        test.nekroHome();
        String lodorPosition = AiTestGame.neighbourOf(AiTestGame.HOME);
        lodorTile = test.place("26", lodorPosition);
        meharXullTile = test.place("24", "105");
        test.place("19", "106");
        test.place("18", "000");
        test.solHomeBeside(lodorPosition);
        test.game.setSpeaker(sol);
    }

    private double value(Player voter, String agenda, String outcome) {
        OptionalDouble value = AgendaValues.of(test.game, voter, agenda, outcome, "");
        assertThat(value).as("%s / %s", agenda, outcome).isPresent();
        return value.getAsDouble();
    }

    private double rivalCost(Player voter, Player owner, double worth) {
        return AgendaValueMath.rivalGainCost(test.game, voter, owner, worth);
    }

    // Each Elect Planet law is worth the user's flat number to the planet's owner, whatever sits on the planet.
    @Test
    void electedPlanetLawsAreWorthTheirTableValueToTheOwner() {
        nekro.addPlanet(LODOR);
        nekro.addPlanet(MEHAR_XULL);
        nekro.addPlanet(WELLON);
        test.units(lodorTile, LODOR, nekro, UnitType.Spacedock, 1);

        assertThat(value(nekro, "core_mining", MEHAR_XULL)).isCloseTo(1, within(1e-9));
        assertThat(value(nekro, "demilitarized_zone", LODOR)).isCloseTo(2, within(1e-9));
        assertThat(value(nekro, "holy_planet_of_ixth", LODOR)).isCloseTo(3, within(1e-9));
        assertThat(value(nekro, "rt_biotic", WELLON)).isCloseTo(1, within(1e-9));
        assertThat(value(nekro, "rt_cybernetic", WELLON)).isCloseTo(1, within(1e-9));
        assertThat(value(nekro, "rt_propulsion", WELLON)).isCloseTo(1, within(1e-9));
        assertThat(value(nekro, "rt_warfare", MEHAR_XULL)).isCloseTo(1, within(1e-9));
        assertThat(value(nekro, "senate_sanctuary", LODOR)).isCloseTo(1, within(1e-9));
        assertThat(value(nekro, "terraforming_initiative", MEHAR_XULL)).isCloseTo(1, within(1e-9));
    }

    // A rival owning the elected planet costs the voter that owner's worth through the usual threat weighting.
    @Test
    void aRivalsElectedPlanetCostsTheVoterItsThreatWeightedWorth() {
        nekro.addPlanet(LODOR);

        assertThat(value(sol, "holy_planet_of_ixth", LODOR)).isCloseTo(-rivalCost(sol, nekro, 3), within(1e-9));
        assertThat(value(sol, "senate_sanctuary", LODOR)).isCloseTo(-rivalCost(sol, nekro, 1), within(1e-9));
        assertThat(value(sol, "holy_planet_of_ixth", LODOR)).isNegative();
    }

    // The trait requirement still applies: Lodor is cultural, so the hazardous and industrial laws can't elect it.
    @Test
    void aPlanetWithoutTheRequiredTraitIsWorthNothing() {
        nekro.addPlanet(LODOR);
        nekro.addPlanet(MEHAR_XULL);

        assertThat(value(nekro, "core_mining", LODOR)).isZero();
        assertThat(value(nekro, "rt_biotic", LODOR)).isZero();
        assertThat(value(nekro, "holy_planet_of_ixth", MEHAR_XULL)).isZero();
        assertThat(value(nekro, "demilitarized_zone", "nowhere")).isZero();
    }

    // Compensated Disarmament turns ground forces into trade goods: good for a seat short of money (Nekro with no
    // trade goods has only Mordai II and Mehar Xull to spend), bad for a seat that already has plenty.
    @Test
    void compensatedDisarmamentOnOwnUnitsIsGoodOnlyWhenShortOfTradeGoods() {
        nekro.addPlanet(MEHAR_XULL);
        test.units(meharXullTile, MEHAR_XULL, nekro, UnitType.Infantry, 2);

        nekro.setTg(0);
        assertThat(value(nekro, "disarmament", MEHAR_XULL)).isPositive();

        nekro.setTg(8);
        assertThat(value(nekro, "disarmament", MEHAR_XULL)).isNegative();
    }

    // Without a need for trade goods, the table aims Compensated Disarmament at the points leader's garrison.
    @Test
    void compensatedDisarmamentIsAimedAtThePointsLeader() {
        test.points(sol, 6);
        test.points(jolnar, 2);
        sol.setTg(8);
        jolnar.setTg(8);
        sol.addPlanet(LODOR);
        test.units(lodorTile, LODOR, sol, UnitType.Infantry, 2);
        double onLeader = value(hacan, "disarmament", LODOR);

        sol.removePlanet(LODOR);
        lodorTile.removeAllUnits(sol.getColor());
        jolnar.addPlanet(LODOR);
        test.units(lodorTile, LODOR, jolnar, UnitType.Infantry, 2);
        double onTrailer = value(hacan, "disarmament", LODOR);

        assertThat(onLeader).isPositive();
        assertThat(onLeader).isGreaterThan(onTrailer);
    }

    // Colonial Redistribution stays on the legacy formula, which already aims it at the leader: the leader dislikes
    // its own planet being chosen most, and the other seats prefer the leader's planet to a trailing rival's.
    @Test
    void colonialRedistributionIsAimedAtThePointsLeader() {
        test.points(sol, 6);
        test.points(jolnar, 3);
        test.points(hacan, 2);
        sol.addPlanet(LODOR);
        test.units(lodorTile, LODOR, sol, UnitType.Infantry, 1);
        double leaderView = value(sol, "redistribution", LODOR);
        List<Double> otherViews = List.of(
                value(hacan, "redistribution", LODOR),
                value(jolnar, "redistribution", LODOR),
                value(nekro, "redistribution", LODOR));
        double onLeader = value(hacan, "redistribution", LODOR);

        sol.removePlanet(LODOR);
        lodorTile.removeAllUnits(sol.getColor());
        jolnar.addPlanet(LODOR);
        test.units(lodorTile, LODOR, jolnar, UnitType.Infantry, 1);
        double onTrailer = value(hacan, "redistribution", LODOR);

        assertThat(leaderView).isNegative();
        assertThat(otherViews).allSatisfy(view -> assertThat(view).isGreaterThan(leaderView));
        assertThat(onLeader).isGreaterThan(onTrailer);
    }
}
