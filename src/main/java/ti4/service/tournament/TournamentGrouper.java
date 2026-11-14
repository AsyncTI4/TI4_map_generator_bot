package ti4.service.tournament;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TournamentGrouper {

    private static final int RESTARTS = 4;
    private static final int SWAP_ATTEMPTS_PER_RESTART = 20_000;

    static final int FULL_FIT_SCORE = 100;
    static final int SHARED_HOUR_BONUS = 5;
    private static final int SPREAD_PENALTY = 10_000;
    private static final int REMATCH_PENALTY = 10_000;

    public record Entrant(String userId, Set<Integer> activeUtcHours) {}

    public record Constraints(Set<String> keepApart, Map<String, Integer> previousGroups) {

        public static Constraints none() {
            return new Constraints(Set.of(), Map.of());
        }

        boolean isActive() {
            return keepApart.size() > 1 || !previousGroups.isEmpty();
        }
    }

    public static List<List<String>> group(List<Entrant> entrants, int groupSize, boolean matchActiveHours, long seed) {
        return group(entrants, groupSize, matchActiveHours, Constraints.none(), seed);
    }

    public static List<List<String>> group(
            List<Entrant> entrants, int groupSize, boolean matchActiveHours, Constraints constraints, long seed) {
        if (entrants.isEmpty()) return List.of();
        Random random = new Random(seed);
        int[] sizes = groupSizes(entrants.size(), groupSize);
        int[][] pairScores = pairScores(entrants, matchActiveHours, constraints);

        int[] best = randomAssignment(entrants.size(), sizes, random);
        if (matchActiveHours || constraints.isActive()) {
            long bestScore = score(best, pairScores);
            for (int restart = 0; restart < RESTARTS; restart++) {
                int[] candidate = restart == 0 ? best.clone() : randomAssignment(entrants.size(), sizes, random);
                climb(candidate, pairScores, random);
                long candidateScore = score(candidate, pairScores);
                if (candidateScore > bestScore) {
                    best = candidate;
                    bestScore = candidateScore;
                }
            }
        }
        return toGroups(best, sizes.length, entrants);
    }

    private static int[][] pairScores(List<Entrant> entrants, boolean matchActiveHours, Constraints constraints) {
        int[][] scores = matchActiveHours ? overlapMatrix(entrants) : new int[entrants.size()][entrants.size()];
        for (int a = 0; a < entrants.size(); a++) {
            for (int b = 0; b < entrants.size(); b++) {
                if (a == b) continue;
                String first = entrants.get(a).userId();
                String second = entrants.get(b).userId();
                if (constraints.keepApart().contains(first)
                        && constraints.keepApart().contains(second)) {
                    scores[a][b] -= SPREAD_PENALTY;
                }
                Integer firstGroup = constraints.previousGroups().get(first);
                if (firstGroup != null
                        && firstGroup.equals(constraints.previousGroups().get(second))) {
                    scores[a][b] -= REMATCH_PENALTY;
                }
            }
        }
        return scores;
    }

    public static int[] groupSizes(int entrantCount, int groupSize) {
        if (entrantCount <= 0) return new int[0];
        int groupCount = (entrantCount + groupSize - 1) / groupSize;
        int[] sizes = new int[groupCount];
        for (int i = 0; i < groupCount; i++) {
            sizes[i] = entrantCount / groupCount + (i < entrantCount % groupCount ? 1 : 0);
        }
        return sizes;
    }

    public static long score(List<List<Entrant>> groups) {
        long total = 0;
        for (List<Entrant> group : groups) {
            for (int a = 0; a < group.size(); a++) {
                for (int b = a + 1; b < group.size(); b++) {
                    total += sharedHours(
                            group.get(a).activeUtcHours(), group.get(b).activeUtcHours());
                }
            }
        }
        return total;
    }

    static int sharedHours(Set<Integer> a, Set<Integer> b) {
        return (int) a.stream().filter(b::contains).count();
    }

    private static int[][] overlapMatrix(List<Entrant> entrants) {
        int n = entrants.size();
        int[] masks = new int[n];
        for (int i = 0; i < n; i++) {
            for (int hour : entrants.get(i).activeUtcHours()) {
                masks[i] |= 1 << hour;
            }
        }
        int[][] overlap = new int[n][n];
        for (int a = 0; a < n; a++) {
            for (int b = 0; b < n; b++) {
                if (a != b) overlap[a][b] = pairFit(masks[a], masks[b]);
            }
        }
        return overlap;
    }

    static int pairFit(int firstHours, int secondHours) {
        int shared = Integer.bitCount(firstHours & secondHours);
        int narrower = Math.min(Integer.bitCount(firstHours), Integer.bitCount(secondHours));
        if (narrower == 0) return 0;
        return shared * FULL_FIT_SCORE / narrower + shared * SHARED_HOUR_BONUS;
    }

    private static int[] randomAssignment(int n, int[] sizes, Random random) {
        List<Integer> slots = new ArrayList<>(n);
        for (int group = 0; group < sizes.length; group++) {
            for (int i = 0; i < sizes[group]; i++) {
                slots.add(group);
            }
        }
        Collections.shuffle(slots, random);
        int[] assignment = new int[n];
        for (int i = 0; i < n; i++) {
            assignment[i] = slots.get(i);
        }
        return assignment;
    }

    private static void climb(int[] assignment, int[][] overlap, Random random) {
        int n = assignment.length;
        if (n < 2) return;
        for (int attempt = 0; attempt < SWAP_ATTEMPTS_PER_RESTART; attempt++) {
            int a = random.nextInt(n);
            int b = random.nextInt(n);
            if (assignment[a] == assignment[b]) continue;
            if (swapGain(assignment, overlap, a, b) > 0) {
                int groupOfA = assignment[a];
                assignment[a] = assignment[b];
                assignment[b] = groupOfA;
            }
        }
    }

    private static long swapGain(int[] assignment, int[][] overlap, int a, int b) {
        int groupOfA = assignment[a];
        int groupOfB = assignment[b];
        long gain = 0;
        for (int other = 0; other < assignment.length; other++) {
            if (other == a || other == b) continue;
            if (assignment[other] == groupOfA) {
                gain += overlap[b][other] - overlap[a][other];
            } else if (assignment[other] == groupOfB) {
                gain += overlap[a][other] - overlap[b][other];
            }
        }
        return gain;
    }

    private static long score(int[] assignment, int[][] overlap) {
        long total = 0;
        for (int a = 0; a < assignment.length; a++) {
            for (int b = a + 1; b < assignment.length; b++) {
                if (assignment[a] == assignment[b]) total += overlap[a][b];
            }
        }
        return total;
    }

    private static List<List<String>> toGroups(int[] assignment, int groupCount, List<Entrant> entrants) {
        List<List<String>> groups = new ArrayList<>();
        for (int group = 0; group < groupCount; group++) {
            groups.add(new ArrayList<>());
        }
        for (int i = 0; i < assignment.length; i++) {
            groups.get(assignment[i]).add(entrants.get(i).userId());
        }
        return groups;
    }
}
