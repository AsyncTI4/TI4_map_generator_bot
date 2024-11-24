package ti4.service.statistics;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.SortHelper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.GamesPage;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;

@UtilityClass
public class ListSlashCommandsUsedService {

    public static void queueReply(SlashCommandInteractionEvent event) {
        StatisticsPipeline.queue(new StatisticsPipeline.StatisticsEvent(event, () -> listSlashCommandsUsed(event)));
    }

    private static void listSlashCommandsUsed(SlashCommandInteractionEvent event) {
        AtomicInteger buttonsPressed = new AtomicInteger();
        AtomicInteger slashCommandsUsed = new AtomicInteger();
        AtomicInteger acsSabod = new AtomicInteger();
        AtomicInteger largestAmountOfButtonsIn1Game = new AtomicInteger();
        AtomicReference<String> largestGame = new AtomicReference<>("");
        boolean useOnlyLastMonth = event.getOption(Constants.ONLY_LAST_MONTH, false, OptionMapping::getAsBoolean);
        Map<String, Integer> slashCommands = new HashMap<>();
        Map<String, Integer> actionCards = new HashMap<>();
        Map<String, Integer> actionCardsPlayed = new HashMap<>();

        GamesPage.consumeAllGames(game -> listSlashCommandsUsed(game, useOnlyLastMonth, slashCommands, actionCards, actionCardsPlayed, largestGame,
            largestAmountOfButtonsIn1Game, buttonsPressed, slashCommandsUsed, acsSabod));

        StringBuilder longMsg = new StringBuilder("The number of button pressed so far recorded is " + buttonsPressed + ". The largest number of buttons pressed in a single game is " + largestAmountOfButtonsIn1Game + " in game " + largestGame + ". The number of slash commands used is " + slashCommandsUsed
            + ". The number of ACs Sabo'd is " + acsSabod + ". The following is the recorded frequency of slash commands \n");
        Map<String, Integer> sortedMapAsc = SortHelper.sortByValue(slashCommands, false);
        for (String command : sortedMapAsc.keySet()) {
            longMsg.append(command).append(": ").append(sortedMapAsc.get(command)).append(" \n");
        }
        longMsg.append("\n The number of times an AC has been Sabo'd is also being tracked. The following is their recorded frequency \n");
        Map<String, Integer> sortedMapAscACs = SortHelper.sortByValue(actionCards, false);
        for (String command : sortedMapAscACs.keySet()) {
            longMsg.append(command).append(": ").append(sortedMapAscACs.get(command)).append(" out of ").append(actionCardsPlayed.get(command)).append(" times played").append(" \n");
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), longMsg.toString());
    }

    private static void listSlashCommandsUsed(Game game, boolean useOnlyLastMonth, Map<String, Integer> slashCommands, Map<String, Integer> actionCards,
                                                Map<String, Integer> actionCardsPlayed, AtomicReference<String> largestGame, AtomicInteger largestAmountOfButtonsIn1Game,
                                                AtomicInteger buttonsPressed, AtomicInteger slashCommandsUsed, AtomicInteger acsSabod) {
        if (useOnlyLastMonth && Helper.getDateDifference(game.getCreationDate(), Helper.getDateRepresentation(System.currentTimeMillis())) > 30) {
            return;
        }
        if (game.getButtonPressCount() > largestAmountOfButtonsIn1Game.get()) {
            largestGame.set(game.getName());
            largestAmountOfButtonsIn1Game.set(game.getButtonPressCount());
        }
        buttonsPressed.addAndGet(game.getButtonPressCount());
        slashCommandsUsed.addAndGet(game.getSlashCommandsRunCount());
        for (String command : game.getAllSlashCommandsUsed().keySet()) {
            int numUsed = game.getAllSlashCommandsUsed().get(command);
            int numUsed2 = 0;
            if (slashCommands.containsKey(command)) {
                numUsed2 = slashCommands.get(command);
            }
            slashCommands.put(command, numUsed + numUsed2);
        }
        if (Helper.getDateDifference(game.getCreationDate(), Helper.getDateRepresentation(1698724000011L)) >= 0) {
            return;
        }
        for (String acName : game.getAllActionCardsSabod().keySet()) {
            int numUsed = game.getAllActionCardsSabod().get(acName);
            int numUsed2 = 0;
            if (actionCards.containsKey(acName)) {
                numUsed2 = actionCards.get(acName);
            }
            acsSabod.addAndGet(numUsed);
            actionCards.put(acName, numUsed + numUsed2);
        }
        for (String acID : game.getDiscardActionCards().keySet()) {
            ActionCardModel ac = Mapper.getActionCard(acID);
            if (ac == null) {
                continue;
            }
            String acName = ac.getName();
            int numUsed = 1;
            int numUsed2 = 0;
            if (actionCardsPlayed.containsKey(acName)) {
                numUsed2 = actionCardsPlayed.get(acName);
            }
            actionCardsPlayed.put(acName, numUsed + numUsed2);
        }
        for (String acID : game.getPurgedActionCards().keySet()) {
            ActionCardModel ac = Mapper.getActionCard(acID);
            if (ac == null) {
                continue;
            }
            String acName = ac.getName();
            int numUsed = 1;
            int numUsed2 = 0;
            if (actionCardsPlayed.containsKey(acName)) {
                numUsed2 = actionCardsPlayed.get(acName);
            }
            actionCardsPlayed.put(acName, numUsed + numUsed2);
        }
    }
}
