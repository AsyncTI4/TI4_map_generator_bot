package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import ti4.game.Game;
import ti4.game.Player;
import ti4.message.MessageHelper;
import ti4.testUtils.BaseTi4Test;

// Cancelling a rider removes the cancelled player's copy, not every copy of that rider on the table.
class AgendaRiderReversalTest extends BaseTi4Test {

    private Game game;
    private Player sol;
    private Player xxcha;
    private Player hacan;

    @BeforeEach
    void setUp() {
        game = new Game();
        sol = game.addPlayer("sol-id", "sol");
        sol.setFaction("sol");
        sol.setColor("blue");
        xxcha = game.addPlayer("xxcha-id", "xxcha");
        xxcha.setFaction("xxcha");
        xxcha.setColor("green");
        hacan = game.addPlayer("hacan-id", "hacan");
        hacan.setFaction("hacan");
        hacan.setColor("yellow");
        game.setCurrentAgendaVote("for", "sol_Leadership Rider;xxcha_Leadership Rider;hacan_Politics Rider");
        game.setCurrentAgendaVote("against", "hacan_Leadership Rider");
    }

    private void reverse(Player announcer, Player riderOwner) {
        try (MockedStatic<MessageHelper> ignored = Mockito.mockStatic(MessageHelper.class)) {
            AgendaRiderHelper.reverseRider("reverse_Leadership Rider", game, announcer, riderOwner);
        }
    }

    @Test
    void onlyTheCancelledPlayersCopyIsRemoved() {
        reverse(xxcha, sol);

        assertThat(game.getCurrentAgendaVotes().get("for")).isEqualTo("xxcha_Leadership Rider;hacan_Politics Rider");
        assertThat(game.getCurrentAgendaVotes().get("against")).isEqualTo("hacan_Leadership Rider");
    }

    @Test
    void theOwnerIsMatchedByColorToo() {
        game.setCurrentAgendaVote("for", "blue_Leadership Rider;green_Leadership Rider");

        reverse(xxcha, sol);

        assertThat(game.getCurrentAgendaVotes().get("for")).isEqualTo("green_Leadership Rider");
    }

    @Test
    void otherRidersOfTheSamePlayerStay() {
        reverse(sol, hacan);

        assertThat(game.getCurrentAgendaVotes().get("for"))
                .isEqualTo("sol_Leadership Rider;xxcha_Leadership Rider;hacan_Politics Rider");
    }

    @Test
    void withoutAKnownOwnerEveryCopyIsRemoved() {
        reverse(sol, null);

        assertThat(game.getCurrentAgendaVotes().get("for")).isEqualTo("hacan_Politics Rider");
    }
}
