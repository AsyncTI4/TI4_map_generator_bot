package ti4.commands2.bothelper;

import java.util.HashMap;
import java.util.Map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.Subcommand;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.SortHelper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;

class ListSlashCommandsUsed extends Subcommand {

    public ListSlashCommandsUsed() {
        super(Constants.LIST_SLASH_COMMANDS_USED, "List the frequency with which slash commands are used");
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.ONLY_LAST_MONTH, "Only include games started in last month? y/n").setRequired(false));
    }

    public void execute(SlashCommandInteractionEvent event) {
        int buttonsPressed = 0;
        int slashCommandsUsed = 0;
        int acsSabod = 0;
        boolean useOnlyLastMonth = false;
        Boolean onlyLastMonth = event.getOption(Constants.ONLY_LAST_MONTH, null, OptionMapping::getAsBoolean);
        if (onlyLastMonth != null) {
            useOnlyLastMonth = true;
        }
        int largestAmountOfButtonsIn1Game = 0;
        String largestGame = "";
        Map<String, Game> mapList = GameManager.getGameNameToGame();
        Map<String, Integer> slashCommands = new HashMap<>();
        Map<String, Integer> actionCards = new HashMap<>();
        Map<String, Integer> actionCardsPlayed = new HashMap<>();
        for (Game game : mapList.values()) {
            if (useOnlyLastMonth && Helper.getDateDifference(game.getCreationDate(), Helper.getDateRepresentation(System.currentTimeMillis())) > 30) {
                continue;
            }
            if (game.getButtonPressCount() > largestAmountOfButtonsIn1Game) {
                largestGame = game.getName();
                largestAmountOfButtonsIn1Game = game.getButtonPressCount();
            }
            buttonsPressed = game.getButtonPressCount() + buttonsPressed;
            slashCommandsUsed = game.getSlashCommandsRunCount() + slashCommandsUsed;
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
                    acsSabod = acsSabod + numUsed;
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
}
