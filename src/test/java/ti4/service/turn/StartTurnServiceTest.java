package ti4.service.turn;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class StartTurnServiceTest extends BaseTi4Test {

    @Test
    void GetMissedSCFollowsText_SkipsZeroStrategyPoolReminder_WhenLeadershipIsOnlyPendingFollow() {
        Game game = createGameWithPlayedStrategyCard(1);
        Player player = game.getPlayer("p2");
        player.setStrategicCC(0);

        String message = StartTurnService.getMissedSCFollowsText(game, player);

        assertTrue(message.contains("Please resolve these before doing anything else:"));
        assertFalse(message.contains("You currently have 0 command tokens in your strategy pool."));
    }

    @Test
    void GetMissedSCFollowsText_KeepsZeroStrategyPoolReminder_WhenNonLeadershipIsPendingFollow() {
        Game game = createGameWithPlayedStrategyCard(2);
        Player player = game.getPlayer("p2");
        player.setStrategicCC(0);

        String message = StartTurnService.getMissedSCFollowsText(game, player);

        assertTrue(message.contains("You currently have 0 command tokens in your strategy pool."));
    }

    private Game createGameWithPlayedStrategyCard(int playedSc) {
        Game game = new Game();
        game.setName("testGame");

        Player scHolder = game.addPlayer("p1", "Player 1");
        scHolder.setFaction("sol");
        scHolder.setColor("blue");
        scHolder.setSCs(Set.of(playedSc));

        Player player = game.addPlayer("p2", "Player 2");
        player.setFaction("hacan");
        player.setColor("yellow");
        player.setSCs(Set.of(8));

        game.setSCPlayed(playedSc, true);
        return game;
    }
}
