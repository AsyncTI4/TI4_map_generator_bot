package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class ButtonHelperActionCardsTest extends BaseTi4Test {

    @Test
    void findTwinningTargetActionCardIdUsesDiscardIdentifierForPurgedCards() {
        Game game = new Game();
        Player player = game.addPlayer("player", "player");
        player.setActionCard("war_machine2", 101);
        game.discardActionCard(player.getUserID(), 101);
        game.setPurgedActionCard("war_machine1");

        Integer purgedDiscardIdentifier = game.getPurgedActionCards().get("war_machine1");

        assertThat(ButtonHelperActionCards.findTwinningTargetActionCardId(game, purgedDiscardIdentifier.toString()))
                .isEqualTo("war_machine1");
    }

    @Test
    void findTwinningTargetActionCardIdFallsBackToLegacyCardNameButtons() {
        Game game = new Game();
        Player player = game.addPlayer("player", "player");
        player.setActionCard("war_machine2", 101);
        game.discardActionCard(player.getUserID(), 101);
        game.setPurgedActionCard("war_machine1");

        assertThat(ButtonHelperActionCards.findTwinningTargetActionCardId(game, "War Machine"))
                .isIn("war_machine1", "war_machine2");
    }
}
