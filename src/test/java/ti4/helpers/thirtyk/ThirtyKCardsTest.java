package ti4.helpers.thirtyk;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.model.DeckModel;
import ti4.model.ExploreModel;
import ti4.model.Mapper;
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

    // Deck sizes are pinned because Mapper only logs JSON import failures; the CSV "Deck number" became copies.
    @Test
    void decksHaveTheExpectedSizesAndCopies() {
        DECK_SIZES.forEach((alias, size) -> {
            DeckModel deck = Mapper.getDeck(alias);
            assertThat(deck).as(alias).isNotNull();
            assertThat(deck.getCardIDs()).as(alias).hasSize(size).doesNotHaveDuplicates();
            assertThat(deck.getSource()).isEqualTo(ComponentSource.thirtyk);
        });
        assertThat(Mapper.getDeck("action_cards_30k").getCardIDs())
                .contains("conflagration30k1", "conflagration30k2")
                .doesNotContain("conflagration30k");
        assertThat(Mapper.getDeck("explores_30k").getCardIDs())
                .contains("imitationfactories30k4", "foresightofindustryb30k4", "tourismbureau30k");
    }

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

    // Play-area explores are tracked as fake relics with the same id; static attachment values live in data.
    @Test
    void crossReferencedDataExists() {
        List<ExploreModel> playArea = Mapper.getExplores().values().stream()
                .filter(c -> c.getSource() == ComponentSource.thirtyk)
                .filter(c -> c.getText().startsWith("Place this card face up in your play area"))
                .toList();
        assertThat(playArea).hasSize(9).allSatisfy(explore -> {
            RelicModel relic = Mapper.getRelic(explore.getId());
            assertThat(relic).as(explore.getId()).isNotNull();
            assertThat(relic.isFakeRelic()).isTrue();
        });
        assertThat(Mapper.getAttachmentInfo("precisionlaserdefenses30k").getSpaceCannonHitsOn())
                .isEqualTo(3);
    }

    @Test
    void baseIdDropsTheCopyNumberOnly() {
        assertThat(ThirtyKCards.baseId("conflagration30k2")).isEqualTo("conflagration30k");
        assertThat(ThirtyKCards.baseId("siphonfundsjmsc4")).isEqualTo("siphonfundsjmsc");
        assertThat(ThirtyKCards.baseId("tourismbureau30k")).isEqualTo("tourismbureau30k");
        // Explore copies in the x3 combined deck carry the bot's extra1/extra2 suffix.
        assertThat(ThirtyKCards.baseId("pilotrecoveryprogram30k2extra1")).isEqualTo("pilotrecoveryprogram30k");
        assertThat(ThirtyKCards.baseId("tourismbureau30kextra2")).isEqualTo("tourismbureau30k");
        assertThat(ThirtyKCards.isActionCard("conflagration30k1")).isTrue();
        assertThat(ThirtyKCards.isActionCard("sabo1")).isFalse();
    }

    // Hooks are cut short unless one of the game's decks holds a 30k card. Once seen, the flag sticks, so 30k
    // cards still in play keep working after a deck is switched away mid-game.
    @Test
    void inUseFollowsTheDecksAndSticks() {
        Game game = new Game();
        assertThat(ThirtyKCards.inUse(game)).isFalse();
        assertThat(game.getStoredValueMap()).doesNotContainKey(ThirtyKCards.IN_USE_KEY);

        game.setExplorationDeckID("explores_pok_te_30k_jmsc");
        assertThat(ThirtyKCards.inUse(game)).isTrue();

        game.setExplorationDeckID("explores_pok");
        assertThat(ThirtyKCards.inUse(game)).isTrue();
    }

    // Combined decks are PoK/TE plus 30k plus JMSC; action cards and explores three times over.
    @Test
    void combinedDecksAreTheSumOfTheirParts() {
        assertThat(cards("action_cards_pok_te_30k_jmsc"))
                .hasSize(3
                        * (cards("action_cards_te").size()
                                + cards("action_cards_30k").size()
                                + cards("action_cards_jmsc").size()))
                .contains("siphonfundsjmsc1extra2")
                .doesNotHaveDuplicates();
        assertThat(cards("relics_pok_te_30k_jmsc"))
                .hasSize(cards("relics_pok_te").size()
                        + cards("relics_30k").size()
                        + cards("relics_jmsc").size());
        assertThat(cards("secret_objectives_pok_te_30k_jmsc"))
                .hasSize(cards("secret_objectives_pok").size()
                        + cards("secret_objectives_30k").size()
                        + cards("secret_objectives_jmsc").size());
        assertThat(cards("explores_pok_te_30k_jmsc"))
                .hasSize(3
                        * (cards("explores_pok").size()
                                + cards("explores_30k").size()
                                + cards("explores_jmsc").size()))
                .contains("tourismbureau30k", "tourismbureau30kextra1", "tourismbureau30kextra2")
                .doesNotHaveDuplicates();
    }

    private static List<String> cards(String deckAlias) {
        return Mapper.getDeck(deckAlias).getCardIDs();
    }
}
