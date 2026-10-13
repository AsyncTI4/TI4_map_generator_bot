package ti4.helpers.thirtyk;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import ti4.image.Mapper;
import ti4.model.DeckModel;
import ti4.model.ExploreModel;
import ti4.model.RelicModel;
import ti4.model.Source.ComponentSource;
import ti4.model.enums.AutomationStatus;
import ti4.testUtils.BaseTi4Test;

class ThirtyKCardsTest extends BaseTi4Test {

    private record Status(String id, AutomationStatus automation, String notes) {}

    // Mapper only logs JSON import failures, so deck sizes are pinned: a card that fails to load drops out of
    // DeckModelTest's view only if its deck entry is removed too, and these counts catch that.
    private static final Map<String, Integer> DECK_SIZES = Map.of(
            "action_cards_30k", 16,
            "explores_30k", 53,
            "relics_30k", 9,
            "secret_objectives_30k", 14,
            "action_cards_jmsc", 24,
            "explores_jmsc", 10,
            "relics_jmsc", 6,
            "secret_objectives_jmsc", 13);

    private static Stream<Status> allThirtyKCards() {
        return Stream.of(
                        Mapper.getActionCards().values().stream()
                                .filter(c -> c.getSource() == ComponentSource.thirtyk)
                                .map(c -> new Status(c.getAlias(), c.getAutomation(), c.getAutomationNotes())),
                        Mapper.getExplores().values().stream()
                                .filter(c -> c.getSource() == ComponentSource.thirtyk)
                                .map(c -> new Status(c.getId(), c.getAutomation(), c.getAutomationNotes())),
                        Mapper.getRelics().values().stream()
                                .filter(c -> c.getSource() == ComponentSource.thirtyk)
                                .map(c -> new Status(c.getAlias(), c.getAutomation(), c.getAutomationNotes())),
                        Mapper.getSecretObjectives().values().stream()
                                .filter(c -> c.getSource() == ComponentSource.thirtyk)
                                .map(c -> new Status(c.getAlias(), c.getAutomation(), c.getAutomationNotes())))
                .flatMap(s -> s);
    }

    @Test
    void everyThirtyKCardDeclaresHowMuchTheBotAutomates() {
        assertThat(allThirtyKCards()).isNotEmpty().allSatisfy(card -> {
            assertThat(card.automation()).as(card.id()).isNotNull();
            if (card.automation().needsPlayerAttention()) {
                assertThat(card.notes()).as(card.id() + " notes").isNotBlank();
            }
        });
    }

    @Test
    void decksHaveTheExpectedSizeAndOnlyThirtyKCards() {
        DECK_SIZES.forEach((alias, size) -> {
            DeckModel deck = Mapper.getDeck(alias);
            assertThat(deck).as(alias).isNotNull();
            assertThat(deck.getCardIDs()).as(alias).hasSize(size).doesNotHaveDuplicates();
            assertThat(deck.getSource()).isEqualTo(ComponentSource.thirtyk);
        });
    }

    @Test
    void deckNumberColumnBecomesCopies() {
        assertThat(Mapper.getDeck("action_cards_30k").getCardIDs())
                .contains("conflagration30k1", "conflagration30k2")
                .doesNotContain("conflagration30k");
        assertThat(Mapper.getDeck("action_cards_jmsc").getCardIDs())
                .contains("siphonfundsjmsc1", "siphonfundsjmsc4", "creationofgloryjmsc");
        assertThat(Mapper.getDeck("explores_30k").getCardIDs())
                .contains(
                        "imitationfactories30k4",
                        "foresightofindustry30k4",
                        "foresightofindustryb30k4",
                        "tourismbureau30k");
    }

    // Explore and relic ids end up inside button ids that are split on `_` (e.g. resolve_explore_<id>_<planet>).
    @Test
    void exploreAndRelicIdsHaveNoUnderscores() {
        Stream.concat(
                        Mapper.getExplores().values().stream()
                                .filter(c -> c.getSource() == ComponentSource.thirtyk)
                                .map(ExploreModel::getId),
                        Mapper.getRelics().values().stream()
                                .filter(c -> c.getSource() == ComponentSource.thirtyk)
                                .map(RelicModel::getAlias))
                .forEach(id -> assertThat(id).doesNotContain("_"));
    }

    // "Place this card face up in your play area" explores are tracked as fake relics with the same id.
    @Test
    void playAreaExploresHaveAFakeRelic() {
        List<ExploreModel> playArea = Mapper.getExplores().values().stream()
                .filter(c -> c.getSource() == ComponentSource.thirtyk)
                .filter(c -> c.getText().startsWith("Place this card face up in your play area"))
                .toList();
        assertThat(playArea).hasSize(9);
        for (ExploreModel explore : playArea) {
            RelicModel relic = Mapper.getRelic(explore.getId());
            assertThat(relic).as(explore.getId()).isNotNull();
            assertThat(relic.isFakeRelic()).isTrue();
        }
    }

    @Test
    void baseIdDropsTheCopyNumberOnly() {
        assertThat(ThirtyKCards.baseId("conflagration30k2")).isEqualTo("conflagration30k");
        assertThat(ThirtyKCards.baseId("siphonfundsjmsc4")).isEqualTo("siphonfundsjmsc");
        assertThat(ThirtyKCards.baseId("tourismbureau30k")).isEqualTo("tourismbureau30k");
        assertThat(ThirtyKCards.isActionCard("conflagration30k1")).isTrue();
        assertThat(ThirtyKCards.isActionCard("sabo1")).isFalse();
    }
}
