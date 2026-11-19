package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.testUtils.BaseTi4Test;

// Agenda game events: every revealed agenda ends in one AGENDA_RESOLVED, which names an outcome or why it was
// discarded. Casting 0 votes is abstaining, so a 0-vote AGENDA_VOTE_CAST never names an outcome; its source says
// whether the player chose to abstain or could not vote, and why.
class AgendaEventsTest extends BaseTi4Test {

    private Game game;
    private Player sol;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.setName("agenda-events-test");
        sol = game.addPlayer("200000000000000001", "sol");
        sol.setFaction("sol");
        sol.setColor("blue");
        game.setTile(new Tile("26", "304"));
        sol.addPlanet("lodor");
    }

    @Test
    void aDiscardNamesTheAgendaAndTheReason() {
        assertThat(AgendaEvents.discardedPayload("covert", AgendaEvents.QUASH))
                .containsEntry("agendaId", "covert")
                .containsEntry("agendaName", "Covert Legislation")
                .containsEntry("discarded", "quash");
    }

    @Test
    void aRevealSaysWhereItCameFrom() {
        assertThat(AgendaEvents.revealedPayload("minister_war", 2, true, false))
                .containsEntry("agendaName", "Minister of War")
                .containsEntry("agendaNumber", 2)
                .containsEntry("fromBottom", true)
                .containsEntry("duringAction", false);
    }

    @Test
    void aVoteNamesItsOutcome() {
        assertThat(AgendaEvents.voteCastPayload("for", 5, AgendaEvents.VOTE, false))
                .containsExactlyInAnyOrderEntriesOf(Map.of("outcome", "for", "votes", 5, "source", "vote"));
    }

    // The vote button remembers the last outcome looked at, so an abstention can arrive with one attached.
    @Test
    void zeroVotesIsAnAbstentionWithNoOutcome() {
        assertThat(AgendaEvents.voteCastPayload("for", 0, AgendaEvents.VOTE, false))
                .containsEntry("votes", 0)
                .doesNotContainKey("outcome");
    }

    @Test
    void hiddenAgendaModeKeepsVotesSecret() {
        assertThat(AgendaEvents.voteCastPayload("for", 5, AgendaEvents.VOTE, true))
                .containsOnlyKeys("source", "hidden");
    }

    @Test
    void aPlayerWithPlanetsCanVote() {
        assertThat(AgendaHelper.cannotVoteReason(game, sol)).isNull();
    }

    @Test
    void aRiderStopsThePlayerVoting() {
        game.setCurrentAgendaVote("for", "sol_Politics Rider");

        assertThat(AgendaHelper.cannotVoteReason(game, sol)).isEqualTo("rider");
        assertThat(AgendaHelper.getVoteTotal(sol, game)[0]).isZero();
    }

    @Test
    void anAssassinatedRepresentativeCannotVote() {
        game.setStoredValue("AssassinatedReps", "sol");

        assertThat(AgendaHelper.cannotVoteReason(game, sol)).isEqualTo("assassinate representative");
    }

    @Test
    void aPlayerWithoutPlanetsCannotVote() {
        sol.removePlanet("lodor");

        assertThat(AgendaHelper.cannotVoteReason(game, sol)).isEqualTo("no planet votes");
    }
}
