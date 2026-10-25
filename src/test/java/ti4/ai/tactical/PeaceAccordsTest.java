package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

// Peace Accords: after Diplomacy, gain a planet outside Mecatol Rex that holds no units and sits in a system adjacent
// to
// a planet the seat controls. The engine lists the planets it will accept; this class ranks them.
class PeaceAccordsTest extends BaseTi4Test {

    private AiTestGame test;
    private String firstRing;
    private String secondRing;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        test.nekro.addAbility("peace_accords");
        List<String> neighbours = MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME).stream()
                .filter(position -> !"x".equals(position))
                .toList();
        firstRing = neighbours.get(0);
        secondRing = neighbours.get(1);
    }

    private List<String> claimed() {
        return PeaceAccords.claims(test.game, test.nekro).stream()
                .map(PeaceAccords.Claim::planet)
                .toList();
    }

    @Test
    void offersAnEmptyPlanetNextToAControlledPlanet() {
        test.place("38", firstRing);

        assertThat(claimed()).containsExactlyInAnyOrder("abyz", "fria");
    }

    @Test
    void ranksThePlanetWorthMostFirst() {
        test.place("38", firstRing);

        assertThat(claimed()).startsWith("abyz");
        assertThat(PeaceAccords.bestValue(test.game, test.nekro)).isGreaterThan(0);
    }

    @Test
    void skipsAPlanetThatHoldsUnits() {
        Tile tile = test.place("38", firstRing);
        test.units(tile, "abyz", test.sol, UnitType.Infantry, 1);

        assertThat(claimed()).containsExactly("fria");
    }

    @Test
    void offersNothingWhenNoEmptyPlanetIsAdjacent() {
        assertThat(PeaceAccords.claims(test.game, test.nekro)).isEmpty();
        assertThat(PeaceAccords.bestValue(test.game, test.nekro)).isZero();
    }

    @Test
    void offersNothingWithoutTheAbility() {
        test.place("38", firstRing);
        test.sol.addAbility("peace_accords");
        test.nekro.removeAbility("peace_accords");

        assertThat(PeaceAccords.claims(test.game, test.nekro)).isEmpty();
    }

    // A planet another seat holds, with nothing on it, is as claimable as an unowned one, and taking it also costs
    // that seat a planet.
    @Test
    void valuesAnEmptyRivalPlanetAboveTheSamePlanetUnowned() {
        test.place("29", firstRing);
        double unowned = valueOf("rarron");

        test.sol.addPlanet("rarron");

        assertThat(claimed()).contains("rarron");
        assertThat(valueOf("rarron")).isGreaterThan(unowned);
    }

    @Test
    void doesNotOfferMecatolRex() {
        test.place("18", firstRing);
        test.place("29", secondRing);

        assertThat(claimed()).contains("rarron").doesNotContain("mr");
    }

    private double valueOf(String planet) {
        return PeaceAccords.claims(test.game, test.nekro).stream()
                .filter(claim -> claim.planet().equals(planet))
                .mapToDouble(PeaceAccords.Claim::value)
                .findFirst()
                .orElseThrow();
    }

    // The planet that completes a public objective is worth more than its resources and influence alone.
    @Test
    void valuesAPlanetThatAdvancesAnObjective() {
        test.place("38", firstRing);
        test.place("29", secondRing);
        double plain = PeaceAccords.bestValue(test.game, test.nekro);

        test.game
                .getRevealedPublicObjectives()
                .put("expand_borders", test.game.getRevealedPublicObjectives().size() + 1);

        assertThat(PeaceAccords.bestValue(test.game, test.nekro)).isGreaterThan(plain);
    }
}
