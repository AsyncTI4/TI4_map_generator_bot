package ti4.helpers.thirtyk;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.image.Mapper;
import ti4.model.ActionCardModel;
import ti4.model.Source.ComponentSource;
import ti4.model.enums.AutomationStatus;
import ti4.testUtils.BaseTi4Test;

class ThirtyKActionCardsTest extends BaseTi4Test {

    // Cards whose resolve step is a target list that can legitimately be empty in this two-player fixture
    // (no far-away homes, no voters yet, neighbours), or that are resolved by the rider prediction buttons.
    private static final Set<String> MAY_HAVE_NO_BUTTONS = Set.of(
            "embassyrider30k",
            "dummyrider30k",
            "publicoutcry30k",
            "priceofinformationjmsc",
            "unverifiedforgeriesjmsc",
            "subjectivejusticejmsc",
            "extendedcampaigning30k",
            "devisiveplot30k");

    private Game game;
    private Player sol;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.setName("thirtyk-ac-test");
        game.setPhaseOfGame("action");
        game.setTile(new Tile("19", "000"));
        sol = addPlayer("sol-id", "sol", "blue");
        addPlayer("hacan-id", "hacan", "green");
        sol.setTg(3);
        game.setActivePlayerID(sol.getUserID());
    }

    @Test
    void everyAutomatedCardOffersResolveButtonsWithinDiscordLimits() {
        List<ActionCardModel> automated = Mapper.getActionCards().values().stream()
                .filter(card -> card.getSource() == ComponentSource.thirtyk)
                .filter(card -> card.getAutomation() != AutomationStatus.MANUAL)
                .toList();
        assertThat(automated).isNotEmpty();
        for (ActionCardModel card : automated) {
            List<Button> buttons = ThirtyKActionCards.resolveButtons(card, sol);
            if (!MAY_HAVE_NO_BUTTONS.contains(ThirtyKCards.baseId(card.getAlias()))) {
                assertThat(buttons).as(card.getAlias()).isNotEmpty();
            }
            assertThat(buttons).as(card.getAlias()).hasSizeLessThanOrEqualTo(25);
            assertThat(buttons)
                    .as(card.getAlias())
                    .allSatisfy(button -> assertThat(button.getCustomId()).hasSizeLessThanOrEqualTo(100));
        }
    }

    @Test
    void manualCardsOfferNothing() {
        assertThat(ThirtyKActionCards.resolveButtons(Mapper.getActionCard("realitydisruptionjmsc1"), sol))
                .isEmpty();
        assertThat(ThirtyKActionCards.resolveButtons(Mapper.getActionCard("ultimatetrickshotjmsc2"), sol))
                .isEmpty();
    }

    @Test
    void compoundInterestOffersOnlyWhatYouCanAfford() {
        // 3 trade goods: 0, 1, 2 or 3 may be placed, never 4.
        assertThat(ThirtyKActionCards.resolveButtons(Mapper.getActionCard("compoundinterest30k"), sol))
                .hasSize(4);
    }

    @Test
    void playingDummyRiderRemembersWhoMayTakeItBack() {
        ThirtyKActionCards.resolveButtons(Mapper.getActionCard("dummyrider30k"), sol);

        assertThat(ThirtyKAcState.isDummyRiderPlayer(game, sol)).isTrue();
    }

    private Player addPlayer(String userId, String faction, String color) {
        Player player = game.addPlayer(userId, faction);
        player.setFaction(faction);
        player.setColor(color);
        return player;
    }
}
