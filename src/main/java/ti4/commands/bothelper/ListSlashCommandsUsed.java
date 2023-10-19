package ti4.commands.bothelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.message.MessageHelper;

public class ListSlashCommandsUsed extends BothelperSubcommandData {
    public ListSlashCommandsUsed() {
        super(Constants.LIST_SLASH_COMMANDS_USED, "List the frequency with which slash commands are used");
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.ONLY_LAST_MONTH, "Only include games started in last month? y/n").setRequired(false));
    }

    public void execute(SlashCommandInteractionEvent event) {
        int buttonsPressed = 0;
        int slashCommandsUsed = 0;
        boolean useOnlyLastMonth = false;
        Boolean onlyLastMonth = event.getOption(Constants.ONLY_LAST_MONTH, null, OptionMapping::getAsBoolean);
        if (onlyLastMonth != null) {
            useOnlyLastMonth = true;
        }
        Map<String, Game> mapList = GameManager.getInstance().getGameNameToGame();
        HashMap<String, Integer> slashCommands = new HashMap<String, Integer>();
        for (Game activeGame : mapList.values()) {
            if(useOnlyLastMonth && Helper.getDateDifference(activeGame.getCreationDate(), Helper.getDateRepresentation(new Date().getTime())) > 30){
                continue;
            }
            buttonsPressed = activeGame.getButtonPressCount() + buttonsPressed;
            slashCommandsUsed = activeGame.getSlashCommandsRunCount() + slashCommandsUsed;
            for (String command : activeGame.getAllSlashCommandsUsed().keySet()) {
                int numUsed = activeGame.getAllSlashCommandsUsed().get(command);
                int numUsed2 = 0;
                if (slashCommands.containsKey(command)) {
                    numUsed2 = slashCommands.get(command);
                }
                slashCommands.put(command, numUsed + numUsed2);
            }
        }
        String longMsg = "The number of button pressed so far recorded is " + buttonsPressed + ". The number of slash commands used is " + slashCommandsUsed
            + ". The following is their recorded frequency \n";
        // List<String> keys = new ArrayList<String>();
        // keys.addAll(slashCommands.keySet());
        // Collections.sort(keys);
        
        Map<String, Integer> sortedMapAsc = sortByValue(slashCommands, false);
         for (String command : sortedMapAsc.keySet()) {
            longMsg = longMsg + command + ": " + sortedMapAsc.get(command) + " \n";
         }
        
        MessageHelper.sendMessageToChannel(event.getChannel(), longMsg);
    }

    private static Map<String, Integer> sortByValue(Map<String, Integer> unsortMap, final boolean order)
    {
        List<Entry<String, Integer>> list = new LinkedList<>(unsortMap.entrySet());

        // Sorting the list based on values
        list.sort((o1, o2) -> order ? o1.getValue().compareTo(o2.getValue()) == 0
                ? o1.getKey().compareTo(o2.getKey())
                : o1.getValue().compareTo(o2.getValue()) : o2.getValue().compareTo(o1.getValue()) == 0
                ? o2.getKey().compareTo(o1.getKey())
                : o2.getValue().compareTo(o1.getValue()));
        return list.stream().collect(Collectors.toMap(Entry::getKey, Entry::getValue, (a, b) -> b, LinkedHashMap::new));

    }

}
