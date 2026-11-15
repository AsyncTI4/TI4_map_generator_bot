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

    @Test
    void narrowWindowPlayersAreNotLeftOverByWideOnes() {
        // 12 players awake 16 hours (AFK settings) and 12 with one 3-hour evening slot in four time zones.
        // Raw overlap would group the wide players together and leave the slots stranded.
        List<Entrant> entrants = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            entrants.add(new Entrant("wide" + i, hours(6 * (i % 4) + 4, 16)));
        }
        for (int i = 0; i < 12; i++) {
            entrants.add(new Entrant("slot" + i, hours(6 * (i % 4) + 10, 3)));
        }
        Map<String, Entrant> byId = entrants.stream().collect(Collectors.toMap(Entrant::userId, Function.identity()));

        for (List<String> group : TournamentGrouper.group(entrants, 6, true, 11)) {
            for (String member : group) {
                Set<Integer> mine = byId.get(member).activeUtcHours();
                int bestTogether = mine.stream()
                        .mapToInt(hour -> (int) group.stream()
                                .filter(other ->
                                        byId.get(other).activeUtcHours().contains(hour))
                                .count())
                        .max()
                        .orElse(0);
                assertThat(bestTogether).as(member + " in " + group).isGreaterThanOrEqualTo(group.size() - 1);
            }
        }
    }

    @Test
    void pairFitCountsHowWellTheNarrowerWindowIsCovered() {
        int evening = 0b111 << 18;
        int awake = 0xFFFF << 6;
        // A fully covered 3-hour slot beats two wide windows that only partly overlap.
        assertThat(TournamentGrouper.pairFit(evening, awake)).isEqualTo(100 + 3 * 5);
        assertThat(TournamentGrouper.pairFit(awake, 0xFFFF << 8)).isEqualTo(14 * 100 / 16 + 14 * 5);
        assertThat(TournamentGrouper.pairFit(evening, 0)).isZero();
    }

    @Test
    void previousWinnersAreSpreadAndRematchesAvoided() {
        // 16 players who played last round in 4 groups of 4 (cohort = previous group); c*p0 won each game.
        List<Entrant> entrants = timezoneCohorts(4, 4);
        Map<String, Integer> previousGroups = entrants.stream()
                .collect(Collectors.toMap(
                        Entrant::userId, entrant -> entrant.userId().charAt(1) - '0'));
        Set<String> previousWinners = Set.of("c0p0", "c1p0", "c2p0", "c3p0");

        List<List<String>> groups = TournamentGrouper.group(
                entrants, 4, true, new TournamentGrouper.Constraints(previousWinners, previousGroups), 3);

        for (List<String> group : groups) {
            assertThat(group.stream().filter(previousWinners::contains)).hasSize(1);
            assertThat(group.stream().map(previousGroups::get).distinct()).hasSize(group.size());
        }
    }

    private static Set<Integer> hours(int start, int length) {
        return IntStream.range(start, start + length)
                .map(hour -> hour % 24)
                .boxed()
                .collect(Collectors.toCollection(HashSet::new));
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
