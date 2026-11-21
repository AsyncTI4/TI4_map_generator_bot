package ti4.ai.scoring;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.IntToDoubleFunction;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Helper;
import ti4.model.Mapper;
import ti4.service.info.ListPlayerInfoService;

@UtilityClass
public class ObjectiveOdds {

    private static final double BOARD_MET = 0.9;
    private static final double[] BOARD_SHORT_BY = {0.44, 0.25, 0.12};
    private static final double SPEND_MET = 0.72;
    private static final double[] SPEND_SHORT_BY = {0.6, 0.6, 0.45};
    private static final int FARTHEST_COUNTED = 3;
    private static final int WITHIN_REACH_STEPS = 1;
    private static final int PUBLICS_FOR_AN_EXTRA_SCORE = 2;
    private static final int SECRET_POINTS = 1;

    private record Chance(double byStatus, boolean metNow, int points, boolean withinReach) {}

    private record LockIn(int lockedPoints, List<Chance> rest) {}

    public record ImperialOdds(double extraPoints, double winSwing) {}

    public static ImperialOdds imperial(Game game, Player player, int mecatolPoints, double secretChance) {
        List<Chance> chances = chances(game, player);
        return new ImperialOdds(extraPoints(chances), winSwing(game, player, chances, mecatolPoints, secretChance));
    }

    public static boolean imperialScoresAnExtraPublic(Game game, Player player) {
        return chances(game, player).stream().filter(Chance::withinReach).count() >= PUBLICS_FOR_AN_EXTRA_SCORE;
    }

    public static double extraPointsWithImperial(Game game, Player seat) {
        return extraPoints(chances(game, seat));
    }

    public static double imperialWinSwing(Game game, Player player, int mecatolPoints, double secretChance) {
        return winSwing(game, player, chances(game, player), mecatolPoints, secretChance);
    }

    private static double extraPoints(List<Chance> chances) {
        List<LockIn> locks = lockInOptions(chances);
        if (locks.isEmpty()) return expectedSecondBest(chances);
        double withoutImperial = expectedBest(chances);
        double withImperial = locks.stream()
                .mapToDouble(lock -> lock.lockedPoints() + expectedBest(lock.rest()))
                .max()
                .orElse(0);
        return Math.max(0, withImperial - withoutImperial);
    }

    private static double winSwing(
            Game game, Player player, List<Chance> chances, int mecatolPoints, double secretChance) {
        int goal = game.getVp();
        if (goal <= 0) return 0;
        int needed = goal - player.getTotalVictoryPoints();
        double without = withSecret(secretChance, needed, left -> reachesWithBest(chances, left));
        List<LockIn> locks = lockInOptions(chances);
        double with = locks.isEmpty()
                ? withSecret(secretChance, needed - mecatolPoints, left -> reachesWithBestTwo(chances, left))
                : locks.stream()
                        .mapToDouble(lock -> withSecret(
                                secretChance,
                                needed - mecatolPoints - lock.lockedPoints(),
                                left -> reachesWithBest(lock.rest(), left)))
                        .max()
                        .orElse(0);
        return Math.max(0, with - without);
    }

    private static double withSecret(double secretChance, int needed, IntToDoubleFunction reaches) {
        return secretChance * reaches.applyAsDouble(needed - SECRET_POINTS)
                + (1 - secretChance) * reaches.applyAsDouble(needed);
    }

    private static List<LockIn> lockInOptions(List<Chance> chances) {
        List<LockIn> options = new ArrayList<>();
        for (int index = 0; index < chances.size(); index++) {
            if (!chances.get(index).metNow()) continue;
            List<Chance> rest = new ArrayList<>(chances);
            Chance locked = rest.remove(index);
            options.add(new LockIn(locked.points(), rest));
        }
        return options;
    }

    private static double reachesWithBest(List<Chance> chances, int needed) {
        if (needed <= 0) return 1;
        return atLeastOne(chances.stream()
                .filter(chance -> chance.points() >= needed)
                .map(Chance::byStatus)
                .toList());
    }

    private static double expectedBest(List<Chance> chances) {
        int most = chances.stream().mapToInt(Chance::points).max().orElse(0);
        double expected = 0;
        for (int points = 1; points <= most; points++) {
            int atLeast = points;
            expected += atLeastOne(chances.stream()
                    .filter(chance -> chance.points() >= atLeast)
                    .map(Chance::byStatus)
                    .toList());
        }
        return expected;
    }

    private static double reachesWithBestTwo(List<Chance> chances, int needed) {
        if (needed <= 0) return 1;
        int most = chances.stream().mapToInt(Chance::points).max().orElse(0);
        double[][] odds = new double[most + 1][most + 1];
        odds[0][0] = 1;
        for (Chance chance : chances) {
            double[][] next = new double[most + 1][most + 1];
            for (int best = 0; best <= most; best++) {
                for (int second = 0; second <= best; second++) {
                    double here = odds[best][second];
                    if (here == 0) continue;
                    next[best][second] += here * (1 - chance.byStatus());
                    int points = chance.points();
                    int newBest = Math.max(best, points);
                    int newSecond = points >= best ? best : Math.max(second, points);
                    next[newBest][newSecond] += here * chance.byStatus();
                }
            }
            odds = next;
        }
        double reach = 0;
        for (int best = 0; best <= most; best++) {
            for (int second = 0; second <= best; second++) {
                if (best + second >= needed) reach += odds[best][second];
            }
        }
        return reach;
    }

    private static double expectedSecondBest(List<Chance> chances) {
        int most = chances.stream().mapToInt(Chance::points).max().orElse(0);
        double expected = 0;
        for (int points = 1; points <= most; points++) {
            int atLeast = points;
            expected += atLeastTwo(chances.stream()
                    .filter(chance -> chance.points() >= atLeast)
                    .map(Chance::byStatus)
                    .toList());
        }
        return expected;
    }

    public static double extraScoreWithImperial(Game game, Player seat) {
        List<Chance> chances = chances(game, seat);
        Optional<Chance> lockedIn =
                chances.stream().filter(Chance::metNow).min(Comparator.comparingDouble(Chance::byStatus));
        if (lockedIn.isEmpty()) return atLeastTwo(byStatus(chances));
        List<Chance> rest = new ArrayList<>(chances);
        rest.remove(lockedIn.get());
        return atLeastOne(byStatus(rest));
    }

    public static double atLeastTwoByStatus(Game game, Player seat) {
        return atLeastTwo(byStatus(chances(game, seat)));
    }

    private static List<Chance> chances(Game game, Player seat) {
        if (!Helper.canPlayerScorePOs(game, seat)) return List.of();
        Wallet wallet = Wallet.of(game, seat);
        List<Chance> chances = new ArrayList<>();
        double bestSpend = 0;
        int spendPoints = 0;
        boolean spendMetNow = false;
        boolean spendWithinReach = false;
        for (String objective : game.getRevealedPublicObjectives().keySet()) {
            if (Mapper.getPublicObjective(objective) == null || ObjectivePolicy.hasScored(game, seat, objective)) {
                continue;
            }
            boolean metNow = ObjectivePolicy.qualifies(game, seat, wallet, objective);
            int points = ObjectivePolicy.victoryPoints(objective);
            Optional<SpendCost> cost = ObjectiveCatalog.spendCost(objective);
            if (cost.isPresent()) {
                double chance = spendChance(wallet, cost.get());
                spendWithinReach |= wallet.shortfall(cost.get(), FARTHEST_COUNTED) <= WITHIN_REACH_STEPS;
                if (chance > bestSpend) spendPoints = points;
                if (chance == bestSpend) spendPoints = Math.max(spendPoints, points);
                bestSpend = Math.max(bestSpend, chance);
                spendMetNow |= metNow;
            } else {
                chances.add(new Chance(
                        boardChance(game, seat, objective),
                        metNow,
                        points,
                        boardShortBy(game, seat, objective) <= WITHIN_REACH_STEPS));
            }
        }
        if (bestSpend > 0) chances.add(new Chance(bestSpend, spendMetNow, spendPoints, spendWithinReach));
        return chances;
    }

    private static List<Double> byStatus(List<Chance> chances) {
        return chances.stream().map(Chance::byStatus).toList();
    }

    static double atLeastOne(List<Double> chances) {
        double none = 1;
        for (double chance : chances) none *= 1 - chance;
        return 1 - none;
    }

    static double spendChance(Wallet wallet, SpendCost cost) {
        int shortBy = wallet.shortfall(cost, FARTHEST_COUNTED);
        return shortBy == 0 ? SPEND_MET : SPEND_SHORT_BY[Math.min(FARTHEST_COUNTED, shortBy) - 1];
    }

    static double boardChance(Game game, Player seat, String objective) {
        int threshold = ListPlayerInfoService.getObjectiveThreshold(objective, game);
        if (threshold <= 0) return 0;
        int shortBy = boardShortBy(game, seat, objective);
        return shortBy == 0 ? BOARD_MET : BOARD_SHORT_BY[Math.min(FARTHEST_COUNTED, shortBy) - 1];
    }

    private static int boardShortBy(Game game, Player seat, String objective) {
        int threshold = ListPlayerInfoService.getObjectiveThreshold(objective, game);
        if (threshold <= 0) return Integer.MAX_VALUE;
        return Math.max(0, threshold - ListPlayerInfoService.getPlayerProgressOnObjective(objective, game, seat));
    }

    static double atLeastTwo(List<Double> chances) {
        double none = 1;
        double one = 0;
        for (double chance : chances) {
            one = one * (1 - chance) + none * chance;
            none *= 1 - chance;
        }
        return Math.max(0, 1 - none - one);
    }
}
