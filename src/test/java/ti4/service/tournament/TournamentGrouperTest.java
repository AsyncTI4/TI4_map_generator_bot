package ti4.service.tournament;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import ti4.service.tournament.TournamentGrouper.Entrant;

class TournamentGrouperTest {

    @Test
    void groupSizesAreAsEvenAsPossible() {
        assertThat(TournamentGrouper.groupSizes(62, 6)).containsExactly(6, 6, 6, 6, 6, 6, 6, 5, 5, 5, 5);
        assertThat(TournamentGrouper.groupSizes(60, 6)).hasSize(10).containsOnly(6);
        assertThat(TournamentGrouper.groupSizes(4, 6)).containsExactly(4);
        assertThat(TournamentGrouper.groupSizes(0, 6)).isEmpty();
    }

    @Test
    void everyoneIsPlacedExactlyOnceAndSeedIsDeterministic() {
        List<Entrant> entrants = timezoneCohorts(4, 6);
        entrants.add(new Entrant("newcomer", Set.of()));

        List<List<String>> first = TournamentGrouper.group(entrants, 6, true, 42);
        List<List<String>> second = TournamentGrouper.group(entrants, 6, true, 42);

        assertThat(first).isEqualTo(second);
        assertThat(first.stream().flatMap(List::stream))
                .containsExactlyInAnyOrderElementsOf(
                        entrants.stream().map(Entrant::userId).toList());
    }

    @Test
    void matchingActiveHoursBeatsPureShuffle() {
        // Four cohorts of six players whose active hours barely overlap across cohorts.
        List<Entrant> entrants = timezoneCohorts(4, 6);
        Map<String, Entrant> byId = entrants.stream().collect(Collectors.toMap(Entrant::userId, Function.identity()));

        long matched = score(TournamentGrouper.group(entrants, 6, true, 7), byId);
        long shuffled = score(TournamentGrouper.group(entrants, 6, false, 7), byId);

        assertThat(matched).isGreaterThan(shuffled);
    }

    private static List<Entrant> timezoneCohorts(int cohorts, int perCohort) {
        List<Entrant> entrants = new ArrayList<>();
        for (int cohort = 0; cohort < cohorts; cohort++) {
            int start = cohort * 6;
            Set<Integer> hours = IntStream.range(start, start + 8)
                    .map(hour -> hour % 24)
                    .boxed()
                    .collect(Collectors.toCollection(HashSet::new));
            for (int i = 0; i < perCohort; i++) {
                entrants.add(new Entrant("c" + cohort + "p" + i, hours));
            }
        }
        return entrants;
    }

    private static long score(List<List<String>> groups, Map<String, Entrant> byId) {
        return TournamentGrouper.score(groups.stream()
                .map(group -> group.stream().map(byId::get).toList())
                .toList());
    }
}
