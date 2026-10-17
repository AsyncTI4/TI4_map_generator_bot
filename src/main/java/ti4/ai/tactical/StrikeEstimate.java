package ti4.ai.tactical;

import java.util.Map;
import ti4.helpers.Units.UnitType;

record StrikeEstimate(
        double spaceWin,
        boolean escaped,
        boolean spaceCombat,
        boolean groundCombat,
        Map<String, Integer> landings,
        Map<String, Double> groundWins,
        Map<String, Integer> groundSent,
        double ourExpectedLoss,
        double ourLossIfWin,
        double theirExpectedLoss,
        double spaceKillChance,
        double groundKillChance,
        double ourLossChance,
        Map<UnitType, Integer> survivors) {}
