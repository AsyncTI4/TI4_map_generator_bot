package ti4.ai.explore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.testUtils.BaseTi4Test;

// A deck is worth the average of its cards still to be drawn, each valued in resources: attachments by the resources
// and influence they add, instants by the better of their options, fragments by how close they bring a relic.
class ExploreDeckTest extends BaseTi4Test {

    private static final double EXACT = 1e-9;

    private AiTestGame test;
    private Tile site;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        site = test.place("28", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.nekro.addPlanet("tequran");
        test.units(site, "tequran", test.nekro, UnitType.Infantry, 1);
        test.aiIsActive("action");
    }

    private double value(String trait) {
        return ExploreDeck.expectedValue(
                ExploreSite.onBoard(test.game, test.nekro, "tequran", new ExploreOutlook(test.game, test.nekro)),
                trait);
    }

    private void deck(String... cards) {
        test.game.setExploreDeck(new ArrayList<>(List.of(cards)));
    }

    // A Dyson Sphere adds 2 resources and 1 influence (2.6), a Paradise World 2 influence (1.2).
    @Test
    void averagesTheCardsLeftInTheDeck() {
        deck("ds", "pw");

        assertThat(value("cultural")).isCloseTo((2 + 0.6 + 1.2) / 2, within(EXACT));
    }

    @Test
    void anEmptyDeckIsWorthNothing() {
        deck();

        assertThat(value("cultural")).isZero();
    }

    // Only the cards of the trait count, wherever they sit in the draw pile.
    @Test
    void countsOnlyTheCardsOfTheTrait() {
        deck("mw", "ds", "rw");

        assertThat(value("hazardous")).isCloseTo((2 + 1) / 2.0, within(EXACT));
    }

    // A first fragment is worth 1; the one that completes a set of three, 2.
    @Test
    void aFragmentIsWorthMoreWhenItCompletesASet() {
        deck("crf1");
        assertThat(value("cultural")).isCloseTo(1.0, within(EXACT));

        test.nekro.addFragment("crf2");
        test.nekro.addFragment("crf3");
        assertThat(value("cultural")).isCloseTo(2.0, within(EXACT));
    }

    // The Demilitarized Zone costs at least 1.5, and the structures it returns to reinforcements on top.
    @Test
    void theDemilitarizedZoneCostsTheStructuresOnThePlanet() {
        deck("dmz");
        assertThat(value("cultural")).isCloseTo(-1.5, within(EXACT));

        test.units(site, "tequran", test.nekro, UnitType.Spacedock, 1);
        assertThat(value("cultural")).isCloseTo(-1.5 - 4, within(EXACT));
    }

    // A research facility gives a planet without a specialty one (0.3), and +1/+1 to a planet that has one.
    @Test
    void aResearchFacilityIsWorthMoreOnAPlanetWithASpecialty() {
        deck("biotic");
        assertThat(value("industrial")).isCloseTo(0.3, within(EXACT));

        Tile tarmann = test.place("22", "302");
        test.nekro.addPlanet("tarmann");
        double withSpecialty = ExploreDeck.expectedValue(
                ExploreSite.onBoard(test.game, test.nekro, "tarmann", new ExploreOutlook(test.game, test.nekro)),
                "industrial");
        assertThat(tarmann).isNotNull();
        assertThat(withSpecialty).isCloseTo(1.6, within(EXACT));
    }

    // Lost Crew draws two action cards, a Derelict Vessel a secret objective.
    @Test
    void drawsAreWorthTheirCards() {
        deck("lc1");
        assertThat(value("frontier")).isCloseTo(2.0, within(EXACT));

        deck("dv1");
        assertThat(value("frontier")).isCloseTo(ExploreValues.SECRET_OBJECTIVE, within(EXACT));
    }

    // A full exploration deck is worth about a resource a card, which is what the planner adds for taking a planet
    // that nobody holds.
    @Test
    void aFullDeckIsWorthAboutOneResourcePerExploration() {
        test.game.setExploreDeck(Mapper.getShuffledDeck("explores_pok"));
        test.nekro.setCommodities(2);

        for (String trait : List.of("cultural", "industrial", "hazardous")) {
            assertThat(value(trait)).as(trait).isBetween(0.4, 1.6);
        }
    }
}
