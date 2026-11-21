package ti4.ai.actioncards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

class ExpertTiersTest extends BaseTi4Test {

    private static final String DEFAULT_DECK = "action_cards_te";

    private static double strength(String name) {
        return ExpertTiers.strengthOfName(name).orElseThrow();
    }

    // The tier list is keyed by card name; a typo would leave a card unranked without anyone noticing.
    @Test
    void everyRankedCardIsInTheDefaultDeck() {
        List<String> deckNames = Mapper.getDeck(DEFAULT_DECK).getCardIDs().stream()
                .map(ActionCardPrior::nameOf)
                .toList();

        for (String name : List.of(
                "Overrule",
                "Master Plan",
                "Ancient Burial Sites",
                "Economic Initiative",
                "Ghost Ship",
                "Counterstroke")) {
            assertThat(deckNames).contains(name);
            assertThat(ExpertTiers.isRanked(name)).as(name).isTrue();
        }
    }

    @Test
    void theBestAndWorstOfATierAreItsEnds() {
        assertThat(strength("Overrule")).isCloseTo(2.2, within(1e-9));
        assertThat(strength("Master Plan")).isCloseTo(1.8, within(1e-9));
        assertThat(strength("Confounding Legal Text")).isCloseTo(1.7, within(1e-9));
        assertThat(strength("Leadership Rider")).isCloseTo(1.4, within(1e-9));
        assertThat(strength("Crisis")).isCloseTo(1.35, within(1e-9));
        assertThat(strength("Brilliance")).isLessThan(1.15);
        assertThat(strength("Economic Initiative")).isCloseTo(1.0, within(1e-9));
        assertThat(strength("Bunker")).isCloseTo(0.8, within(1e-9));
        assertThat(strength("Infiltrate")).isCloseTo(0.75, within(1e-9));
        assertThat(strength("Ancient Burial Sites")).isCloseTo(0.45, within(1e-9));
    }

    @Test
    void aCardInTheMiddleOfATierIsInterpolated() {
        assertThat(strength("Imperial Rider")).isCloseTo(1.7 - 0.3 / 5, within(1e-9));
    }

    @Test
    void strongerTiersAlwaysBeatWeakerOnes() {
        assertThat(strength("Master Plan")).isGreaterThan(strength("Confounding Legal Text"));
        assertThat(strength("Leadership Rider")).isGreaterThan(strength("Crisis"));
        assertThat(strength("Brilliance")).isGreaterThan(strength("Economic Initiative"));
        assertThat(strength("Bunker")).isGreaterThan(strength("Infiltrate"));
    }

    @Test
    void cardsAfterOverruleAreNotRanked() {
        assertThat(ExpertTiers.strengthOfName("Strategize")).isEmpty();
        assertThat(ExpertTiers.strengthOfName("Sabotage")).isEmpty();
        assertThat(ExpertTiers.isRanked("Spy")).isFalse();
    }
}
