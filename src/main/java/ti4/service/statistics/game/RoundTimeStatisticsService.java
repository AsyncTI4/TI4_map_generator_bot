package ti4.service.statistics.game;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.commands.statistics.GameStatisticsFilterer;
import ti4.map.Game;
import ti4.map.GamesPage;
import ti4.message.MessageHelper;

@UtilityClass
class RoundTimeStatisticsService {

    public static void getRoundTimes(SlashCommandInteractionEvent event) {
        Map<String, Long> timeCount = new HashMap<>();
        Map<String, Integer> amountCount = new HashMap<>();

        GamesPage.consumeAllGames(
            GameStatisticsFilterer.getGamesFilter(event),
            game -> getRoundTimes(game, timeCount, amountCount)
        );

        StringBuilder sb = new StringBuilder();
        sb.append("Time Per Phase:").append("\n");
        timeCount.forEach((key, value) -> sb.append(key).append(": ")
            .append(StringUtils.leftPad(convertMillisecondsToDays((float) value / amountCount.get(key)), 4)).append(" days (based on ").append(amountCount.get(key)).append(" games)")
            .append("\n"));
        MessageHelper.sendMessageToThread((MessageChannelUnion) event.getMessageChannel(), "Time per Phase", sb.toString());
    }

    private static void getRoundTimes(Game game, Map<String, Long> timeCount, Map<String, Integer> amountCount) {
        for (int round = 1; round <= game.getRound(); round++) {
            processPhaseTime(
                game,
                timeCount,
                amountCount,
                "Round " + round + " Strategy And Action Phases",
                "startTimeOfRound" + round + "Strategy",
                "startTimeOfRound" + round + "StatusScoring");

            processPhaseTime(
                game,
                timeCount,
                amountCount,
                "Round " + round + " Status Phase",
                "startTimeOfRound" + round + "StatusScoring",
                "startTimeOfRound" + round + "Agenda1");

            processPhaseTime(
                game,
                timeCount,
                amountCount,
                "Round " + round + " Agenda 1",
                "startTimeOfRound" + round + "Agenda1",
                "startTimeOfRound" + round + "Agenda2");

            processPhaseTime(
                game,
                timeCount,
                amountCount,
                "Round " + round + " Agenda 2",
                "startTimeOfRound" + round + "Agenda2",
                "startTimeOfRound" + (round + 1) + "Strategy");
        }
    }

    private static void processPhaseTime(Game game, Map<String, Long> timeCount, Map<String, Integer> amountCount, String phaseName, String key1, String key2) {
        if (game.getStoredValue(key1).isEmpty() || game.getStoredValue(key2).isEmpty()) {
            return;
        }
        amountCount.put(phaseName, 1 + amountCount.getOrDefault(phaseName, 0));
        long startTime = Long.parseLong(game.getStoredValue(key1));
        long endTime = Long.parseLong(game.getStoredValue(key2));
        timeCount.put(phaseName, endTime - startTime + timeCount.getOrDefault(phaseName, 0L));
    }

    private static String convertMillisecondsToDays(float milliseconds) {
        final float millisecondsInADay = TimeUnit.DAYS.toMillis(1);
        float days = milliseconds / millisecondsInADay;
        DecimalFormat decimalFormat = new DecimalFormat("#.00");
        return decimalFormat.format(days);
    }
}
