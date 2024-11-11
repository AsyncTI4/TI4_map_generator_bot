package ti4.commands.statistics.commandsToConvert;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.Subcommand;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;

public class ListSlashCommandsUsed extends Subcommand {

    public ListSlashCommandsUsed() {
        super(Constants.LIST_SLASH_COMMANDS_USED, "List the frequency with which slash commands are used");
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.ONLY_LAST_MONTH, "Only include games started in last month? y/n").setRequired(false));
    }

    public void execute(SlashCommandInteractionEvent event) {
        Map<String, Integer> slashCommands = new HashMap<>();
        Map<String, Integer> actionCards = new HashMap<>();
        Map<String, Integer> actionCardsPlayed = new HashMap<>();
        UsedStats stats = new UsedStats();
        Boolean onlyLastMonth = event.getOption(Constants.ONLY_LAST_MONTH, null, OptionMapping::getAsBoolean);
        boolean useOnlyLastMonth = onlyLastMonth != null;

        int currentPage = 0;
        GameManager.PagedGames pagedGames;
        do {
            pagedGames = GameManager.getGamesPage(currentPage++);
            listSlashCommands(pagedGames.getGames(), slashCommands, actionCards, actionCardsPlayed, stats, useOnlyLastMonth);
        } while (pagedGames.hasNextPage());

        StringBuilder longMsg = new StringBuilder("The number of button pressed so far recorded is " + stats.buttonsPressed +
                ". The largest number of buttons pressed in a single game is " + stats.largestAmountOfButtonsIn1Game + " in game " + stats.largestGame +
                ". The number of slash commands used is " + stats.slashCommandsUsed + ". The number of ACs Sabo'd is " + stats.acsSabod +
                ". The following is the recorded frequency of slash commands \n");
        Map<String, Integer> sortedMapAsc = sortByValue(slashCommands, false);
        for (String command : sortedMapAsc.keySet()) {
            longMsg.append(command).append(": ").append(sortedMapAsc.get(command)).append(" \n");
        }
        longMsg.append("\n The number of times an AC has been Sabo'd is also being tracked. The following is their recorded frequency \n");
        Map<String, Integer> sortedMapAscACs = sortByValue(actionCards, false);
        for (String command : sortedMapAscACs.keySet()) {
            longMsg.append(command).append(": ").append(sortedMapAscACs.get(command)).append(" out of ").append(actionCardsPlayed.get(command)).append(" times played").append(" \n");
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), longMsg.toString());
    }

    private static void listSlashCommands(List<Game> games, Map<String, Integer> slashCommands, Map<String, Integer> actionCards,
                                          Map<String, Integer> actionCardsPlayed, UsedStats usedStats, boolean useOnlyLastMonth) {
        for (Game game : games) {
            if (useOnlyLastMonth && Helper.getDateDifference(game.getCreationDate(), Helper.getDateRepresentation(System.currentTimeMillis())) > 30) {
                continue;
            }
            if (game.getButtonPressCount() > usedStats.largestAmountOfButtonsIn1Game) {
                usedStats.largestGame = game.getName();
                usedStats.largestAmountOfButtonsIn1Game = game.getButtonPressCount();
            }
            usedStats.buttonsPressed = game.getButtonPressCount() + usedStats.buttonsPressed;
            usedStats.slashCommandsUsed = game.getSlashCommandsRunCount() + usedStats.slashCommandsUsed;
            for (String command : game.getAllSlashCommandsUsed().keySet()) {
                int numUsed = game.getAllSlashCommandsUsed().get(command);
                int numUsed2 = 0;
                if (slashCommands.containsKey(command)) {
                    numUsed2 = slashCommands.get(command);
                }
                slashCommands.put(command, numUsed + numUsed2);
            }
            if (Helper.getDateDifference(game.getCreationDate(), Helper.getDateRepresentation(1698724000011L)) < 0) {
                for (String acName : game.getAllActionCardsSabod().keySet()) {
                    int numUsed = game.getAllActionCardsSabod().get(acName);
                    int numUsed2 = 0;
                    if (actionCards.containsKey(acName)) {
                        numUsed2 = actionCards.get(acName);
                    }
                    usedStats.acsSabod = usedStats.acsSabod + numUsed;
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
    }

    public static Map<String, Integer> sortByValue(Map<String, Integer> unsortMap, boolean order) {
        List<Entry<String, Integer>> list = new ArrayList<>(unsortMap.entrySet());
        // Sorting the list based on values
        list.sort((o1, o2) -> order ? o1.getValue().compareTo(o2.getValue()) == 0
            ? o1.getKey().compareTo(o2.getKey())
            : o1.getValue().compareTo(o2.getValue())
            : o2.getValue().compareTo(o1.getValue()) == 0
                ? o2.getKey().compareTo(o1.getKey())
                : o2.getValue().compareTo(o1.getValue()));
        return list.stream().collect(Collectors.toMap(Entry::getKey, Entry::getValue, (a, b) -> b, LinkedHashMap::new));
    }

    private static class UsedStats {
        int buttonsPressed = 0;
        int slashCommandsUsed = 0;
        int acsSabod = 0;
        int largestAmountOfButtonsIn1Game = 0;
        String largestGame = "";
    }

}
