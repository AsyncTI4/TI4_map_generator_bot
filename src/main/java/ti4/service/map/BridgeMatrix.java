package ti4.service.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiPredicate;
import javax.annotation.Nullable;

public final class BridgeMatrix {

    public static final BridgeMatrix NONE = new BridgeMatrix(new boolean[6][6]);
    static final List<String> SIDE_NAMES = List.of("N", "NE", "SE", "S", "SW", "NW");

    public record Pair(int low, int high) {

        public Set<Integer> rimGaps(boolean clockwiseFromLow) {
            Set<Integer> gaps = new HashSet<>();
            int from = clockwiseFromLow ? low : high, to = clockwiseFromLow ? high : low;
            for (int gap = from; gap != to; gap = (gap + 1) % 6) gaps.add(gap);
            return gaps;
        }

        boolean crosses(Pair other) {
            return low < other.low && other.low < high && high < other.high
                    || other.low < low && low < other.high && other.high < high;
        }
    }

    private final boolean[][] leads;
    private final Set<Integer> sides;

    private BridgeMatrix(boolean[][] leads) {
        this.leads = leads;
        Set<Integer> used = new TreeSet<>();
        for (int side = 0; side < 6; side++) {
            if (entersAt(side) || exitsAt(side)) used.add(side);
        }
        sides = Collections.unmodifiableSet(used);
    }

    @Nullable
    public static BridgeMatrix parse(@Nullable String stored) {
        if (stored == null || !CustomHyperlaneService.isValidStoredMatrix(stored)) {
            return null;
        }
        int[][] grid = CustomHyperlaneService.parseMatrix(stored);
        boolean[][] leads = new boolean[6][6];
        for (int from = 0; from < 6; from++) {
            for (int to = 0; to < 6; to++) {
                leads[from][to] = from != to && grid[from][to] == 1;
            }
        }
        return new BridgeMatrix(leads);
    }

    public boolean leads(int from, int to) {
        return leads[from][to];
    }

    public boolean isEmpty() {
        return sides().isEmpty();
    }

    public boolean entersAt(int side) {
        for (int to = 0; to < 6; to++) if (leads[side][to]) return true;
        return false;
    }

    public boolean exitsAt(int side) {
        for (int from = 0; from < 6; from++) if (leads[from][side]) return true;
        return false;
    }

    public Set<Integer> sides() {
        return sides;
    }

    public Set<Integer> exitsFrom(int entered, boolean bothWays) {
        Set<Integer> exits = new TreeSet<>();
        for (int to = 0; to < 6; to++) {
            if (leads[entered][to] || bothWays && leads[to][entered]) exits.add(to);
        }
        return exits;
    }

    public Set<Integer> entriesLeadingTo(int exit, boolean bothWays) {
        Set<Integer> entries = new TreeSet<>();
        for (int from = 0; from < 6; from++) {
            if (leads[from][exit] || bothWays && leads[exit][from]) entries.add(from);
        }
        return entries;
    }

    public List<Pair> pairs() {
        List<Pair> pairs = new ArrayList<>();
        for (int a = 0; a < 6; a++) {
            for (int b = a + 1; b < 6; b++) {
                if (leads[a][b] || leads[b][a]) pairs.add(new Pair(a, b));
            }
        }
        return List.copyOf(pairs);
    }

    public List<List<Boolean>> rimLayoutsWithoutSharing() {
        List<Pair> pairs = pairs();
        List<List<Boolean>> layouts = new ArrayList<>();
        for (int mask = 0; mask < 1 << pairs.size(); mask++) {
            List<Boolean> layout = new ArrayList<>();
            Set<Integer> used = new HashSet<>();
            boolean shares = false;
            for (int i = 0; i < pairs.size() && !shares; i++) {
                boolean clockwiseFromLow = (mask & 1 << i) != 0;
                layout.add(clockwiseFromLow);
                for (int gap : pairs.get(i).rimGaps(clockwiseFromLow)) shares |= !used.add(gap);
            }
            if (!shares) layouts.add(List.copyOf(layout));
        }
        return List.copyOf(layouts);
    }

    public Optional<String> layoutProblem() {
        List<Pair> pairs = pairs();
        for (int side = 0; side < 6; side++) {
            List<String> using = new ArrayList<>();
            for (Pair pair : pairs) {
                if (pair.low() == side || pair.high() == side) using.add(describe(pair));
            }
            if (using.size() > 1) {
                return Optional.of(SIDE_NAMES.get(side) + " is used by " + String.join(" and ", using)
                        + ". A side can carry one bridge lane (no splits or merges).");
            }
        }
        for (Pair pair : pairs) {
            for (Pair other : pairs) {
                if (pair.crosses(other)) {
                    return Optional.of(describe(pair) + " and " + describe(other) + " would cross on the rim.");
                }
            }
        }
        if (!pairs.isEmpty() && rimLayoutsWithoutSharing().isEmpty()) {
            List<String> lanes = pairs.stream().map(this::describe).toList();
            return Optional.of(String.join(", ", lanes) + " can't each get their own stretch of the rim.");
        }
        return Optional.empty();
    }

    public String describe(Pair pair) {
        String low = SIDE_NAMES.get(pair.low()), high = SIDE_NAMES.get(pair.high());
        if (leads[pair.low()][pair.high()] && leads[pair.high()][pair.low()]) return low + " ↔ " + high;
        return leads[pair.low()][pair.high()] ? low + " → " + high : high + " → " + low;
    }

    public List<BridgeEnd> ends() {
        List<BridgeEnd> ends = new ArrayList<>();
        for (int side : sides()) ends.add(new BridgeEnd(side, entersAt(side), exitsAt(side)));
        return List.copyOf(ends);
    }

    public BridgeMatrix keepingPairs(BiPredicate<Integer, Integer> keep) {
        boolean[][] kept = new boolean[6][6];
        for (int a = 0; a < 6; a++) {
            for (int b = 0; b < 6; b++) {
                kept[a][b] = leads[a][b] && keep.test(Math.min(a, b), Math.max(a, b));
            }
        }
        return new BridgeMatrix(kept);
    }

    public String stored() {
        int[][] grid = new int[6][6];
        for (int a = 0; a < 6; a++) {
            for (int b = 0; b < 6; b++) grid[a][b] = leads[a][b] ? 1 : 0;
        }
        return CustomHyperlaneService.formatMatrix(grid);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BridgeMatrix matrix && stored().equals(matrix.stored());
    }

    @Override
    public int hashCode() {
        return stored().hashCode();
    }

    @Override
    public String toString() {
        return stored();
    }
}
