package ti4.service.tournament;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.service.tournament.TournamentResults.Advancement;
import ti4.service.tournament.TournamentResults.GameResult;
import ti4.service.tournament.TournamentResults.Standing;

class TournamentResultsTest {

    private static final List<GameResult> RESULTS = List.of(
            new GameResult(1, "pbd1", true, List.of("a"), standings("a", 10, "b", 8, "c", 6)),
            new GameResult(2, "pbd2", true, List.of("d"), standings("d", 10, "e", 8, "f", 7)),
            new GameResult(3, "pbd3", false, List.of(), standings("g", 9, "h", 9, "i", 2)));

    @Test
    void winnersAdvanceFirstThenRunnersUpByPoints() {
        Advancement advancement = TournamentResults.advance(RESULTS, 4);

        assertThat(advancement.winners()).containsExactly("a", "d");
        assertThat(advancement.wildcards()).containsExactly("b", "e");
        assertThat(advancement.tiedAtCut()).isEmpty();
        // Players of a game that is still running never advance automatically.
        assertThat(advancement.unfinishedGames()).containsExactly("pbd3");
        assertThat(advancement.advancing()).doesNotContain("g", "h");
    }

    @Test
    void aTieAtTheLastWildcardSpotIsReported() {
        Advancement advancement = TournamentResults.advance(RESULTS, 3);

        assertThat(advancement.wildcards()).hasSize(1);
        assertThat(advancement.tiedAtCut()).containsExactlyInAnyOrder("b", "e");
        assertThat(TournamentResults.advance(RESULTS, 2).wildcards()).isEmpty();
    }

    private static List<Standing> standings(Object... idsAndPoints) {
        List<Standing> standings = new ArrayList<>();
        for (int i = 0; i < idsAndPoints.length; i += 2) {
            standings.add(new Standing((String) idsAndPoints[i], (Integer) idsAndPoints[i + 1]));
        }
        return standings;
    }
}
