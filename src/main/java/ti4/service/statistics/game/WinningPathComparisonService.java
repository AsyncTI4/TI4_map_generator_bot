package ti4.service.statistics.game;

import static ti4.helpers.StringHelper.ordinal;

import java.text.NumberFormat;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
public class WinningPathComparisonService {

    public static String compareWinningPathToAllOthers(String winningPath, int playerCount, int victoryPointTotal) {
        StringBuilder sb = new StringBuilder();
        Map<String, Integer> winningPathCounts =
                WinningPathCacheService.getWinningPathCounts(playerCount, victoryPointTotal);
        int gamesWithWinnerCount = winningPathCounts.values().stream().reduce(0, Integer::sum);
        if (gamesWithWinnerCount >= 100) {
            int winningPathCount = winningPathCounts.getOrDefault(winningPath, 1);
            double winningPathPercent = winningPathCount / (double) gamesWithWinnerCount;
            String winningPathCommonality = getWinningPathCommonality(winningPathCounts, winningPathCount);
            sb.append("Out of ")
                    .append(gamesWithWinnerCount)
                    .append(" similar games (")
                    .append(victoryPointTotal)
                    .append(" victory points, ")
                    .append(playerCount)
                    .append(" player)")
                    .append(", this path has been seen ")
                    .append(winningPathCount - 1)
                    .append(" times before. It's the ")
                    .append(winningPathCommonality)
                    .append(" most common path (out of ")
                    .append(winningPathCounts.size())
                    .append(" paths) at ")
                    .append(formatPercent(winningPathPercent))
                    .append(" of games.")
                    .append("\n");
            if (winningPathCount == 1) {
                sb.append("🥳__**An async first! May your victory live on for all to see!**__🥳")
                        .append("\n");
            } else if (winningPathPercent <= 0.005) {
                sb.append("🎉__**Few have traveled your path! We celebrate your boldness!**__🎉")
                        .append("\n");
            } else if (winningPathPercent <= 0.01) {
                sb.append("🎉__**Who needs a conventional win? Not you!**__🎉").append("\n");
            }
        }
        return sb.toString();
    }

    private static String getWinningPathCommonality(Map<String, Integer> winningPathCounts, int winningPathCount) {
        int commonality = 1;
        for (int i : winningPathCounts.values()) {
            if (i > winningPathCount) {
                commonality++;
            }
        }
        return commonality == 1 ? "" : ordinal(commonality);
    }

    private static String formatPercent(double d) {
        NumberFormat numberFormat = NumberFormat.getPercentInstance();
        numberFormat.setMinimumFractionDigits(1);
        return numberFormat.format(d);
    }
}
