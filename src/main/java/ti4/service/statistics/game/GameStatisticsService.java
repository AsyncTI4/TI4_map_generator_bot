package ti4.service.statistics.game;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.helpers.Constants;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.statistics.StatisticsPipeline;

@UtilityClass
public class GameStatisticsService {

    public static void queueReply(SlashCommandInteractionEvent event) {
        String statisticToShow = event.getOption(Constants.GAME_STATISTIC, null, OptionMapping::getAsString);
        GameStatTypes statType = GameStatTypes.fromString(statisticToShow);
        if (statType == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not determine stat type.");
            return;
        }
        StatisticsPipeline.queue(event, () -> getGameStatistics(event, statType));
    }

    private void getGameStatistics(SlashCommandInteractionEvent event, GameStatTypes statType) {
        try {
            switch (statType) {
                case UNLEASH_THE_NAMES -> AllNamesStatisticsService.sendAllNames(event);
                case HIGHEST_SPENDERS -> SpendToWinCorrelationStatisticsService.calculateSpendToWinCorrelation(event);
                case GAME_LENGTH -> GameLengthStatisticsService.showGameLengths(event, 3650);
                case GAME_LENGTH_4MO -> GameLengthStatisticsService.showGameLengths(event, 120);
                case FACTIONS_PLAYED -> MostPlayedFactionsStatisticsService.showMostPlayedFactions(event);
                case AVERAGE_TURNS -> FactionAverageTurnsInGameStatisticsService.averageTurnsInAGameByFaction(event);
                case COLOURS_PLAYED -> MostPlayerColorService.getMostPlayedColour(event);
                case FACTION_WINS -> MostWinningFactionsStatisticsService.getMostWinningFactions(event);
                case PHASE_TIMES -> RoundTimeStatisticsService.getRoundTimes(event);
                case SOS_SCORED -> VictoryPointsScoredStatisticsService.listScoredVictoryPoints(event);
                case FACTION_WIN_PERCENT -> FactionWinPercentStatisticsService.getFactionWinPercent(event);
                case FACTION_PERFORMANCE -> FactionPerformanceStatisticsService.showFactionPerformance(event);
                case COLOUR_WINS -> MostWinningColorStatisticsService.showMostWinningColor(event);
                case GAME_COUNT -> GameCountStatisticsService.getGameCount(event);
                case WINNING_PATH -> WinningPathsStatisticsService.showWinningPaths(event);
                case SUPPORT_WIN_COUNT -> WinningPathsStatisticsService.showWinsWithSupport(event);
                default -> MessageHelper.sendMessageToChannel(event.getChannel(), "Unknown Statistic: " + statType);
            }
        } catch (Exception e) {
            BotLogger.error("Failed to process statistic: " + statType, e);
        }
    }
}
