package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// Turning off forced scoring order warns first when scoring the queue would end the game.
class HelperQueuedWinnerTest extends BaseTi4Test {

    private Game game;
    private Player sol;
    private Player hacan;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setVp(10);
        sol = addPlayer("sol", "blue");
        sol.addSC(1);
        hacan = addPlayer("hacan", "green");
        hacan.addSC(2);
    }

    private Player addPlayer(String faction, String color) {
        Player player = game.addPlayer(faction + "-id", faction);
        player.setFaction(faction);
        player.setColor(color);
        return player;
    }

    private void giveCustomPoints(Player player, String name, int points) {
        Integer index = game.addCustomPO(name, points);
        game.scorePublicObjective(player.getUserID(), index);
    }

    private void queuePublic(Player player, String name, int points) {
        Integer index = game.addCustomPO(name, points);
        game.setStoredValue("queueToScorePOs", game.getStoredValue("queueToScorePOs") + player.getFaction() + "*");
        game.setStoredValue(player.getFaction() + "queuedPOScore", String.valueOf(index));
    }

    @Test
    void queuedScoreReachingTheGoalIsAWinner() {
        giveCustomPoints(hacan, "Banked", 8);
        queuePublic(hacan, "Queued Stage II", 2);

        assertThat(Helper.firstQueuedPlayerWhoWouldWin(game)).contains(hacan);
    }

    @Test
    void queuedScoreShortOfTheGoalIsNotAWinner() {
        giveCustomPoints(hacan, "Banked", 7);
        queuePublic(hacan, "Queued Stage II", 2);

        assertThat(Helper.firstQueuedPlayerWhoWouldWin(game)).isEmpty();
    }

    @Test
    void playersWhoDidNotQueueAreIgnored() {
        giveCustomPoints(sol, "Banked", 9);
        queuePublic(hacan, "Queued Stage I", 1);

        assertThat(Helper.firstQueuedPlayerWhoWouldWin(game)).isEmpty();
    }
}
