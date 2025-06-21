package ti4.service.statistics.player;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.statistics.PlayerStatTypes;
import ti4.service.statistics.StatisticsPipeline;

@UtilityClass
public class PlayerStatisticsService {

    public static void queueReply(SlashCommandInteractionEvent event) {
        String statisticToShow = event.getOption(Constants.PLAYER_STATISTIC, null, OptionMapping::getAsString);
        PlayerStatTypes statType = PlayerStatTypes.fromString(statisticToShow);
        if (statType == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Unknown Statistic: " + statisticToShow);
            return;
        }
        StatisticsPipeline.queue(event, () -> getPlayerStatistics(event, statType));
    }

    private void getPlayerStatistics(SlashCommandInteractionEvent event, PlayerStatTypes statType) {
        switch (statType) {
            case PLAYER_WIN_PERCENT -> PlayerWinPercentStatisticsService.showPlayerWinPercent(event);
            case PLAYER_GAME_COUNT -> PlayerGameCountStatisticsService.showPlayerGameCount(event);
            default -> MessageHelper.sendMessageToChannel(event.getChannel(), "Unknown Statistic: " + statType);
        }
    }

    private String getEventName(PlayerStatTypes statType) {
        return PlayerStatisticsService.class.getSimpleName() + ":" + statType;
    }
}
