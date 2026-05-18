package ti4.helpers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;

class StatusHelperTest {

    @Test
    void statusHomeworkCompletionRequiresRedistributionConfirmation() {
        Game game = new Game();
        game.setRound(2);

        Player player = new Player("user-1", "player-one", game);
        player.setFaction("sol");
        game.setCurrentACDrawStatusInfo("_sol");

        assertFalse(StatusHelper.hasPlayerFinishedStatusHomework(game, player));

        StatusHelper.markStatusHomeworkFinished(game, player);

        assertTrue(StatusHelper.hasPlayerFinishedStatusHomework(game, player));
    }

    @Test
    void statusHomeworkCompletionIsTrackedPerRoundAndPlayer() {
        Game game = new Game();
        game.setRound(2);

        Player finishedPlayer = new Player("user-1", "player-one", game);
        finishedPlayer.setFaction("sol");
        Player waitingPlayer = new Player("user-2", "player-two", game);
        waitingPlayer.setFaction("hacan");

        StatusHelper.markStatusHomeworkFinished(game, finishedPlayer);

        assertTrue(StatusHelper.hasPlayerFinishedStatusHomework(game, finishedPlayer));
        assertFalse(StatusHelper.hasPlayerFinishedStatusHomework(game, waitingPlayer));

        game.setRound(3);

        assertFalse(StatusHelper.hasPlayerFinishedStatusHomework(game, finishedPlayer));
    }
}
