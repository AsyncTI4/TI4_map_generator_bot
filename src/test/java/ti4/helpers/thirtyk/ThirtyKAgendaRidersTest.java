package ti4.helpers.thirtyk;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Agenda votes are stored per outcome as "identifier_votes;identifier_Rider Name". The 30k rider cards read and
 * extend that format, so the parsing and the copy rules are pinned here.
 */
class ThirtyKAgendaRidersTest {

    @Test
    void voteEntriesAreParsedAndExtended() {
        Map<String, String> votes = Map.of("for", "sol_6;hacan_Trade Rider");

        assertThat(ThirtyKAgendaRiders.riderEntries(votes))
                .extracting(ThirtyKAgendaRiders.RiderEntry::rider)
                .containsExactly("Trade Rider");
        assertThat(ThirtyKAgendaRiders.numericVoteOutcomes(votes, Set.of("sol")))
                .containsExactly("for");
        assertThat(ThirtyKAgendaRiders.numericVoteOutcomes(votes, Set.of("hacan")))
                .isEmpty();
        assertThat(ThirtyKAgendaRiders.withEntry("empty", "sol_5")).isEqualTo("sol_5");
        assertThat(ThirtyKAgendaRiders.withEntry("hacan_3", "sol_5")).isEqualTo("hacan_3;sol_5");
        // Public Outcry is renamed so the tally treats it as a rider, not a voter.
        assertThat(ThirtyKAgendaRiders.renameEntry(
                        "sol_3;red_Public Outcry", "red_Public Outcry", "red_Public Outcry Rider"))
                .isEqualTo("sol_3;red_Public Outcry Rider");
    }

    // Rulings: Hide within the Fine Print / Objection Gambit only follow non-Politics riders on For/Against,
    // never Public Outcry; the three 30k prediction cards forbid voting.
    @Test
    void copyRulesFollowTheRulings() {
        Map<String, String> votes = new LinkedHashMap<>();
        votes.put("for", "sol_6;hacan_Trade Rider;xxcha_Politics Rider;red_Public Outcry Rider");
        votes.put("against", "nekro_Embassy Rider");
        votes.put("mr", "jolnar_Technology Rider");

        assertThat(ThirtyKAgendaRiders.copyableRiders(votes))
                .extracting(ThirtyKAgendaRiders.RiderEntry::entry)
                .containsExactly("hacan_Trade Rider", "nekro_Embassy Rider");
        assertThat(ThirtyKAgendaRiders.isCopyable("for", "Public Outcry")).isFalse();
        assertThat(ThirtyKAgendaRiders.opposingOutcome("Against")).isEqualTo("for");
        assertThat(ThirtyKAgendaRiders.opposingOutcome("mr")).isNull();
        assertThat(ThirtyKAgendaRiders.forbidsVoting("Dummy Rider")).isTrue();
        assertThat(ThirtyKAgendaRiders.forbidsVoting("Trade Rider")).isFalse();
    }
}
