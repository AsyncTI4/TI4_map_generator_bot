package ti4.helpers.thirtyk;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Agenda votes are stored per outcome as "identifier_votes;identifier_Rider Name". The 30k rider cards read and
 * extend that format, so the parsing is pinned here.
 */
class ThirtyKAgendaRidersTest {

    @Test
    void opposingOutcomeOnlyExistsForForAndAgainst() {
        assertThat(ThirtyKAgendaRiders.opposingOutcome("for")).isEqualTo("against");
        assertThat(ThirtyKAgendaRiders.opposingOutcome("Against")).isEqualTo("for");
        // Ruling: Objection Gambit / Hide within the Fine Print only work on For/Against agendas.
        assertThat(ThirtyKAgendaRiders.opposingOutcome("mr")).isNull();
    }

    @Test
    void onlyNonPoliticsRidersOnForOrAgainstAreCopyable() {
        Map<String, String> votes = new LinkedHashMap<>();
        votes.put("for", "sol_6;hacan_Trade Rider;xxcha_Politics Rider");
        votes.put("against", "letnev_Sanction;nekro_Embassy Rider");
        votes.put("mr", "jolnar_Technology Rider");

        assertThat(ThirtyKAgendaRiders.copyableRiders(votes))
                .extracting(ThirtyKAgendaRiders.RiderEntry::entry)
                .containsExactly("hacan_Trade Rider", "nekro_Embassy Rider");
    }

    @Test
    void numericVotesAreNotRiders() {
        Map<String, String> votes = Map.of("for", "sol_6;hacan_Trade Rider");

        assertThat(ThirtyKAgendaRiders.riderEntries(votes))
                .extracting(ThirtyKAgendaRiders.RiderEntry::rider)
                .containsExactly("Trade Rider");
        assertThat(ThirtyKAgendaRiders.numericVoteOutcomes(votes, Set.of("sol")))
                .containsExactly("for");
        assertThat(ThirtyKAgendaRiders.numericVoteOutcomes(votes, Set.of("hacan")))
                .isEmpty();
    }

    @Test
    void publicOutcryIsRenamedSoTheTallyTreatsItAsARider() {
        // Entries without "Rider" in them count their owner as a voter in AgendaHelper.getWinningVoters.
        String renamed = ThirtyKAgendaRiders.renameEntry(
                "sol_3;red_Public Outcry", "red_Public Outcry", "red_Public Outcry Rider");

        assertThat(renamed).isEqualTo("sol_3;red_Public Outcry Rider");
    }

    @Test
    void ridersThatForbidVoting() {
        assertThat(ThirtyKAgendaRiders.forbidsVoting("Embassy Rider")).isTrue();
        assertThat(ThirtyKAgendaRiders.forbidsVoting("Dummy Rider")).isTrue();
        assertThat(ThirtyKAgendaRiders.forbidsVoting("Public Outcry")).isTrue();
        assertThat(ThirtyKAgendaRiders.forbidsVoting("Trade Rider")).isFalse();
    }

    @Test
    void withEntryHandlesTheEmptyMarker() {
        assertThat(ThirtyKAgendaRiders.withEntry("empty", "sol_5")).isEqualTo("sol_5");
        assertThat(ThirtyKAgendaRiders.withEntry(null, "sol_5")).isEqualTo("sol_5");
        assertThat(ThirtyKAgendaRiders.withEntry("hacan_3", "sol_5")).isEqualTo("hacan_3;sol_5");
    }
}
