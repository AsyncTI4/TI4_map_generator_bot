package ti4.helpers.thirtyk;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

/**
 * Trade Ships, Extended Campaigning, Foregone Discoveries and friends let two players transact as if neighbours.
 * Grants expire on their own (turn or action phase), because nothing clears them at end of turn.
 */
class ThirtyKTransactionsTest extends BaseTi4Test {

    private Game game;
    private Player sol;
    private Player hacan;
    private Player xxcha;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.setName("thirtyk-transactions-test");
        game.setPhaseOfGame("action");
        sol = addPlayer("sol-id", "sol", "blue");
        hacan = addPlayer("hacan-id", "hacan", "green");
        xxcha = addPlayer("xxcha-id", "xxcha", "red");
        game.setActivePlayerID(sol.getUserID());
    }

    @Test
    void turnGrantCoversBothDirectionsOnlyForThatPair() {
        ThirtyKTransactions.allowForThisTurn(game, sol, hacan);

        assertThat(ThirtyKTransactions.mayTransact(game, sol, hacan)).isTrue();
        assertThat(ThirtyKTransactions.mayTransact(game, hacan, sol)).isTrue();
        assertThat(ThirtyKTransactions.mayTransact(game, sol, xxcha)).isFalse();
    }

    @Test
    void turnGrantEndsWhenTheTurnPasses() {
        ThirtyKTransactions.allowForThisTurn(game, sol, hacan);

        // The active player's next turn, or another player's turn, both end the grant.
        sol.setNumberOfTurns(sol.getNumberOfTurns() + 1);
        assertThat(ThirtyKTransactions.mayTransact(game, sol, hacan)).isFalse();
    }

    @Test
    void actionPhaseGrantSurvivesTurnsButNotThePhase() {
        ThirtyKTransactions.allowForThisActionPhase(game, sol, hacan);

        game.setActivePlayerID(xxcha.getUserID());
        assertThat(ThirtyKTransactions.mayTransact(game, sol, hacan)).isTrue();

        game.setPhaseOfGame("agenda");
        assertThat(ThirtyKTransactions.mayTransact(game, sol, hacan)).isFalse();
    }

    @Test
    void storedValueAvoidsTheSaveFormatSeparators() {
        ThirtyKTransactions.allowForThisTurn(game, sol, hacan);
        ThirtyKTransactions.allowForThisActionPhase(game, sol, xxcha);

        assertThat(game.getStoredValue(ThirtyKTransactions.GRANTS_KEY)).doesNotContain(",", ":");
    }

    private Player addPlayer(String userId, String faction, String color) {
        Player player = game.addPlayer(userId, faction);
        player.setFaction(faction);
        player.setColor(color);
        return player;
    }
}
