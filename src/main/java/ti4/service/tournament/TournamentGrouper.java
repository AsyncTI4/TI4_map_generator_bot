package ti4.service.tournament;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TournamentGrouper {

    private static final int RESTARTS = 4;
    private static final int SWAP_ATTEMPTS_PER_RESTART = 20_000;

    public record Entrant(String userId, Set<Integer> activeUtcHours) {}

    public static List<List<String>> group(List<Entrant> entrants, int groupSize, boolean matchActiveHours, long seed) {
        if (entrants.isEmpty()) return List.of();
        Random random = new Random(seed);
        int[] sizes = groupSizes(entrants.size(), groupSize);
        int[][] overlap = overlapMatrix(entrants);

        int[] best = randomAssignment(entrants.size(), sizes, random);
        if (matchActiveHours) {
            long bestScore = score(best, overlap);
            for (int restart = 0; restart < RESTARTS; restart++) {
                int[] candidate = restart == 0 ? best.clone() : randomAssignment(entrants.size(), sizes, random);
                climb(candidate, overlap, random);
                long candidateScore = score(candidate, overlap);
                if (candidateScore > bestScore) {
                    best = candidate;
                    bestScore = candidateScore;
                }
            }
        }
        return toGroups(best, sizes.length, entrants);
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
                overlap[a][b] = a == b ? 0 : Integer.bitCount(masks[a] & masks[b]);
            }
        }
        return overlap;
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
