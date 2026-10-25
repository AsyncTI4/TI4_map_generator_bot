package ti4.ai.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Planet;
import ti4.testUtils.BaseTi4Test;

// With a trait objective revealed, the seat claims the planet of the trait it leads in first, even when another
// planet in the same system is worth slightly more on its own. Torkan is cultural (0 resources, 3 influence); Tequran
// is
// hazardous (2 resources, 0 influence) and the better planet by itself.
class IconStakeClaimOrderTest extends BaseTi4Test {

    private AiTestGame test;
    private List<Planet> mellonAndZohbat;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setRound(1);
        test.aiIsActive("action");
        test.nekroHome();
        test.place("33", "201");
        test.place("25", "202");
        test.place("28", "203");
        mellonAndZohbat = test.game.getTileByPosition("203").getPlanetUnitHolders().stream()
                .map(Planet.class::cast)
                .toList();
    }

    private List<String> claimOrder() {
        return mellonAndZohbat.stream()
                .sorted(IconStake.claimOrder(test.game, test.nekro))
                .map(Planet::getName)
                .toList();
    }

    @Test
    void keepsTheBetterPlanetFirstWithoutATraitObjective() {
        // Influence counts as much as resources, so the 0/3 Torkan is the better planet.

        test.nekro.addPlanet("corneeq");
        test.nekro.addPlanet("quann");

        assertThat(claimOrder()).containsExactly("torkan", "tequran");
    }

    @Test
    void prefersTheLeadingTraitForCorner() {
        test.nekro.addPlanet("corneeq");
        test.nekro.addPlanet("quann");
        test.game.getRevealedPublicObjectives().put("corner", 1);

        assertThat(IconStake.leadingPublicTrait(test.game, test.nekro)).contains("cultural");
        assertThat(claimOrder()).containsExactly("torkan", "tequran");
    }

    @Test
    void ignoresTheObjectiveOnceScored() {
        test.nekro.addPlanet("corneeq");
        test.nekro.addPlanet("quann");
        test.game.getRevealedPublicObjectives().put("corner", 1);
        test.game.scorePublicObjective(test.nekro.getUserID(), 1);

        assertThat(IconStake.leadingPublicTrait(test.game, test.nekro)).isEmpty();
        assertThat(claimOrder()).containsExactly("torkan", "tequran");
    }

    @Test
    void followsTheTraitWithTheMostPlanets() {
        test.nekro.addPlanet("tequran");
        test.game.getRevealedPublicObjectives().put("corner", 1);

        assertThat(IconStake.leadingPublicTrait(test.game, test.nekro)).contains("hazardous");
    }
}
