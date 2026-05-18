package ti4.helpers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.json.PersistenceManager;
import ti4.testUtils.BaseTi4Test;

class StatusHelperTest extends BaseTi4Test {

    @AfterEach
    void tearDown() {
        PersistenceManager.deleteJsonFile("GameMessages.json");
    }

    @Test
    void statusHomeworkCompletionUsesGameMessageTracking() {
        Game game = new Game();
        game.setName("status-homework-completion");
        game.setRound(2);
        StatusHelper.initializeStatusHomeworkTracking(game);

        Player finishedPlayer = new Player("user-1", "player-one", game);
        finishedPlayer.setFaction("sol");
        Player waitingPlayer = new Player("user-2", "player-two", game);
        waitingPlayer.setFaction("hacan");

        assertFalse(StatusHelper.hasPlayerFinishedStatusHomework(game, finishedPlayer));
        assertFalse(StatusHelper.hasPlayerFinishedStatusHomework(game, waitingPlayer));

        StatusHelper.markStatusHomeworkFinished(game, finishedPlayer);

        assertTrue(StatusHelper.hasPlayerFinishedStatusHomework(game, finishedPlayer));
        assertFalse(StatusHelper.hasPlayerFinishedStatusHomework(game, waitingPlayer));
    }

    @Test
    void statusActionCardTrackingNoLongerDependsOnGameStateField() {
        Game game = new Game();
        game.setName("status-homework-ac-draw");
        game.setRound(2);
        StatusHelper.initializeStatusHomeworkTracking(game);

        Player player = new Player("user-1", "player-one", game);
        player.setFaction("sol");

        assertFalse(StatusHelper.hasPlayerDrawnStatusActionCards(game, player));

        StatusHelper.markPlayerDrewStatusActionCards(game, player);

        assertTrue(StatusHelper.hasPlayerDrawnStatusActionCards(game, player));

        game.setRound(3);
        StatusHelper.initializeStatusHomeworkTracking(game);

        assertFalse(StatusHelper.hasPlayerDrawnStatusActionCards(game, player));
    }
}
