package ti4.service.info;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.info.KnownCardsService.KnownCardType;
import ti4.testUtils.BaseTi4Test;

class KnownCardsServiceTest extends BaseTi4Test {

    private Game game;
    private Player viewer;
    private Player target;
    private Player bystander;

    @BeforeEach
    void setUp() {
        game = new Game();
        viewer = createPlayer("viewer", "yssaril", "blue");
        target = createPlayer("target", "hacan", "yellow");
        bystander = createPlayer("bystander", "sol", "black");
        LinkedHashMap<String, Player> players = new LinkedHashMap<>();
        players.put(viewer.getUserID(), viewer);
        players.put(target.getUserID(), target);
        players.put(bystander.getUserID(), bystander);
        game.setPlayers(players);
        game.setSecretObjectives(new ArrayList<>());
    }

    @Test
    void duplicateActionCardsAreListedOncePerCopy() {
        target.setActionCard("sh1");
        target.setActionCard("sh2");
        target.setActionCard("sabo1");

        KnownCardsService.recordHandShown(viewer, target, KnownCardType.ACTION_CARD);

        String description = onlyEmbedDescription(viewer);
        assertThat(StringUtils.countMatches(description, "- Shields Holding")).isEqualTo(2);
        assertThat(description).contains("- Sabotage");
    }

    @Test
    void playingOneCopyRemovesExactlyOneKnownCopy() {
        target.setActionCard("sh1");
        target.setActionCard("sh2");
        KnownCardsService.recordHandShown(viewer, target, KnownCardType.ACTION_CARD);

        game.discardActionCard(target.getUserID(), target.getActionCards().get("sh2"), true);

        String description = onlyEmbedDescription(viewer);
        assertThat(StringUtils.countMatches(description, "- Shields Holding")).isEqualTo(1);
        assertThat(description).doesNotContain("since reveal");
    }

    @Test
    void playingAnotherCopyOfTheSameNameStillRemovesTheKnownCard() {
        // The viewer only saw "Shields Holding"; they cannot tell sh1 from sh2, so playing either one
        // must remove the card they know about rather than reveal that a second copy was in hand.
        target.setActionCard("sh1");
        target.setActionCard("sh2");
        KnownCardsService.recordCardShown(viewer, target, KnownCardType.ACTION_CARD, "Shields Holding");

        game.discardActionCard(target.getUserID(), target.getActionCards().get("sh2"), true);

        assertThat(KnownCardsService.getKnownCardHandEmbeds(game, viewer)).isEmpty();
    }

    @Test
    void playingAnUnknownCardLeavesKnownCardsAlone() {
        target.setActionCard("sh1");
        target.setActionCard("sabo1");
        KnownCardsService.recordCardShown(viewer, target, KnownCardType.ACTION_CARD, "Shields Holding");

        game.discardActionCard(target.getUserID(), target.getActionCards().get("sabo1"), true);

        String description = onlyEmbedDescription(viewer);
        assertThat(description).contains("- Shields Holding").doesNotContain("Sabotage");
        assertThat(description).doesNotContain("since reveal");
    }

    @Test
    void hiddenRemovalKeepsTheCardAndCountsASteal() {
        target.setActionCard("sh1");
        target.setActionCard("sabo1");
        KnownCardsService.recordHandShown(viewer, target, KnownCardType.ACTION_CARD);

        target.removeActionCard(target.getActionCards().get("sh1"));

        String description = onlyEmbedDescription(viewer);
        assertThat(description).contains("- Shields Holding (1 steal since reveal)");
        assertThat(description).contains("- Sabotage (1 steal since reveal)");
    }

    @Test
    void secretObjectiveDiscardIsCountedAndScoringRemovesTheCard() {
        target.setSecret("baf");
        target.setSecret("bam");
        KnownCardsService.recordCardShown(viewer, target, KnownCardType.SECRET_OBJECTIVE, "Betray a Friend");

        game.discardSecretObjective(target.getUserID(), target.getSecrets().get("bam"));

        assertThat(onlyEmbedDescription(viewer)).contains("- Betray a Friend (1 discard since reveal)");

        game.scoreSecretObjective(target.getUserID(), target.getSecrets().get("baf"));

        assertThat(KnownCardsService.getKnownCardHandEmbeds(game, viewer)).isEmpty();
    }

    @Test
    void seeingTheWholeHandAgainResetsTheCount() {
        target.setActionCard("sh1");
        target.setActionCard("sabo1");
        KnownCardsService.recordHandShown(viewer, target, KnownCardType.ACTION_CARD);
        target.removeActionCard(target.getActionCards().get("sabo1"));

        KnownCardsService.recordHandShown(viewer, target, KnownCardType.ACTION_CARD);

        String description = onlyEmbedDescription(viewer);
        assertThat(description).contains("- Shields Holding").doesNotContain("Sabotage");
        assertThat(description).doesNotContain("since reveal");
    }

    @Test
    void cardShownToAllIsKnownByEveryoneButTheOwner() {
        target.setActionCard("sh1");

        KnownCardsService.recordCardShownToAll(game, target, KnownCardType.ACTION_CARD, "Shields Holding");

        assertThat(onlyEmbedDescription(viewer)).contains("- Shields Holding");
        assertThat(onlyEmbedDescription(bystander)).contains("- Shields Holding");
        assertThat(KnownCardsService.getKnownCardHandEmbeds(game, target)).isEmpty();
    }

    @Test
    void sectionsAndEmbedsOnlyAppearWhenSomethingIsKnown() {
        target.setActionCard("sh1");

        assertThat(KnownCardsService.getKnownCardHandEmbeds(game, viewer)).isEmpty();

        KnownCardsService.recordCardShown(viewer, target, KnownCardType.ACTION_CARD, "Shields Holding");

        // Only the target has known cards, and only action cards, so one embed with no SO section.
        List<MessageEmbed> embeds = KnownCardsService.getKnownCardHandEmbeds(game, viewer);
        assertThat(embeds).hasSize(1);
        assertThat(embeds.getFirst().getTitle()).isEqualTo("target - " + target.getFactionNameOrColor());
        assertThat(embeds.getFirst().getDescription()).contains("Action Cards:").doesNotContain("Secret Objectives:");
    }

    private String onlyEmbedDescription(Player knower) {
        List<MessageEmbed> embeds = KnownCardsService.getKnownCardHandEmbeds(game, knower);
        assertThat(embeds).hasSize(1);
        return embeds.getFirst().getDescription();
    }

    private Player createPlayer(String userId, String faction, String color) {
        Player player = new Player(userId, userId, game);
        player.setFaction(faction);
        player.setColor(color);
        return player;
    }
}
