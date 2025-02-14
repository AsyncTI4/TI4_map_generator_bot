package ti4.service.statistics.game;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.statistics.GameStatisticsFilterer;
import ti4.helpers.Helper;
import ti4.helpers.SortHelper;
import ti4.map.Game;
import ti4.map.GamesPage;
import ti4.message.MessageHelper;

@UtilityClass
class GameLengthStatisticsService {

    static void showGameLengths(SlashCommandInteractionEvent event, int pastDays) {
        AtomicInteger atomicNum = new AtomicInteger();
        AtomicInteger atomicTotal = new AtomicInteger();
        Map<String, Integer> endedGames = new HashMap<>();

        GamesPage.consumeAllGames(
                GameStatisticsFilterer.getGamesFilter(event),
                game -> calculate(game, pastDays, atomicNum, atomicTotal, endedGames));

        int num = atomicNum.get();
        int total = atomicTotal.get();

        StringBuilder longMsg = new StringBuilder("The number of games that finished in the last " + pastDays
                + " days is " + num + ". They are listed below based on the number of days it took to complete\n");
        if (num != 0) {
            Map<String, Integer> sortedMapAsc = SortHelper.sortByValue(endedGames, false);
            int num2 = 0;
            for (String command : sortedMapAsc.keySet()) {
                num2++;
                longMsg.append(num2)
                        .append(". ")
                        .append(command)
                        .append(": ")
                        .append(sortedMapAsc.get(command))
                        .append(" \n");
            }
            longMsg.append("\n The average completion time of these games is: ")
                    .append(total / num)
                    .append("\n");
        }
        MessageHelper.sendMessageToThread(
                (MessageChannelUnion) event.getMessageChannel(), "Game Lengths", longMsg.toString());
    }

    private void calculate(
            Game game, int pastDays, AtomicInteger num, AtomicInteger total, Map<String, Integer> endedGames) {
        if (game.isHasEnded()
                && game.getWinner().isPresent()
                && game.getPlayerCountForMap() > 2
                && Helper.getDateDifference(
                                game.getEndedDateString(), Helper.getDateRepresentation(System.currentTimeMillis()))
                        < pastDays) {
            num.getAndIncrement();
            int dif = Helper.getDateDifference(game.getCreationDate(), game.getEndedDateString());
            endedGames.put(game.getName() + " (" + game.getPlayerCountForMap() + "p, " + game.getVp() + "pt)", dif);
            total.addAndGet(dif);
        }
    }
}
