package ti4.helpers.thirtyk;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

/**
 * Trade Ships, Extended Campaigning, Foregone Discoveries and friends let two players transact as if neighbours.
 * Like existing decks, "this turn" state is cleared at end of turn and "this action phase" state when the
 * strategy phase starts; nothing is written in games that never use a 30k card.
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
        game.setAcDeckID("action_cards_30k");
        game.setPhaseOfGame("action");
        sol = addPlayer("sol-id", "sol", "blue");
        hacan = addPlayer("hacan-id", "hacan", "green");
        xxcha = addPlayer("xxcha-id", "xxcha", "red");
        game.setActivePlayerID(sol.getUserID());
    }

    @Test
    void grantCoversBothDirectionsOnlyForThatPair() {
        ThirtyKTransactions.allowForThisTurn(game, sol, hacan);

        assertThat(ThirtyKTransactions.mayTransact(game, hacan, sol)).isTrue();
        assertThat(ThirtyKTransactions.mayTransact(game, sol, xxcha)).isFalse();
        assertThat(game.getStoredValue(ThirtyKTransactions.GRANTS_KEY)).doesNotContain(",", ":");
    }

    // Turn grants survive turn start running again but not end of turn, so back-to-back turns by the same
    // player don't carry them over. Action-phase grants survive end of turn but not the phase.
    @Test
    void grantsLastForTheirTurnOrPhase() {
        ThirtyKTransactions.allowForThisTurn(game, sol, hacan);
        ThirtyKTransactions.allowForThisActionPhase(game, sol, xxcha);

        ThirtyKSecretHooks.onTurnStart(game, sol);
        ThirtyKRelicHooks.onTurnStart(game, sol);
        assertThat(ThirtyKTransactions.mayTransact(game, sol, hacan)).isTrue();

        ThirtyKTurnState.clearEndOfTurn(game);
        assertThat(ThirtyKTransactions.mayTransact(game, sol, hacan)).isFalse();
        assertThat(ThirtyKTransactions.mayTransact(game, sol, xxcha)).isTrue();

        game.setPhaseOfGame("agenda");
        assertThat(ThirtyKTransactions.mayTransact(game, sol, xxcha)).isFalse();
        ThirtyKTurnState.clearActionPhase(game);
        game.setPhaseOfGame("action");
        assertThat(ThirtyKTransactions.mayTransact(game, sol, xxcha)).isFalse();
    }

    // Outside a turn (agenda phase) a "this turn" grant lasts until the strategy phase starts.
    @Test
    void grantWithoutActivePlayerLastsUntilTheStrategyPhase() {
        game.setActivePlayerID(null);
        game.setPhaseOfGame("agendawaiting");
        ThirtyKTransactions.allowForThisTurn(game, sol, hacan);

        assertThat(ThirtyKTransactions.mayTransact(game, sol, hacan)).isTrue();
        ThirtyKTurnState.clearActionPhase(game);
        assertThat(ThirtyKTransactions.mayTransact(game, sol, hacan)).isFalse();
    }

    // The hooks run in every game; the legacy Public Outcry shares the 30k card's name.
    @Test
    void gamesWithoutThirtyKCardsAreLeftUntouched() {
        game.setAcDeckID("action_cards_te");
        game.setCurrentAgendaVote("for", "sol_Public Outcry");

        ThirtyKSecretHooks.onTurnStart(game, sol);
        ThirtyKRelicHooks.onTurnStart(game, sol);
        ThirtyKTurnState.clearEndOfTurn(game);
        ThirtyKTurnState.clearActionPhase(game);
        ThirtyKActionCardHooks.onRiderPlaced(game, sol, "for", "Public Outcry");
        ThirtyKActionCardHooks.onAgendaVotesReset(game);

        assertThat(game.getStoredValueMap().keySet())
                .noneMatch(key -> key.toLowerCase().startsWith("thirtyk") || key.startsWith("thk"));
        assertThat(game.getCurrentAgendaVotes().get("for")).isEqualTo("sol_Public Outcry");
    }

    @Test
    void thirtyKPublicOutcryBecomesARider() {
        game.setCurrentAgendaVote("for", "sol_Public Outcry");
        // The legacy card with the same name stays a plain entry even in a 30k game.
        ThirtyKActionCardHooks.onRiderPlaced(game, sol, "for", "Public Outcry");
        assertThat(game.getCurrentAgendaVotes().get("for")).isEqualTo("sol_Public Outcry");

        ThirtyKAcState.markRiderCard(game, sol, "Public Outcry");

        ThirtyKActionCardHooks.onRiderPlaced(game, sol, "for", "Public Outcry");

        assertThat(game.getCurrentAgendaVotes().get("for")).isEqualTo("sol_Public Outcry Rider");
    }

    private Player addPlayer(String userId, String faction, String color) {
        Player player = game.addPlayer(userId, faction);
        player.setFaction(faction);
        player.setColor(color);
        return player;
    }
}
