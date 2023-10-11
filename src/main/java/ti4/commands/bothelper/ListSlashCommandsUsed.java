package ti4.commands.bothelper;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.TimeUtil;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.message.MessageHelper;

public class ListSlashCommandsUsed extends BothelperSubcommandData {
    public ListSlashCommandsUsed() {
        super(Constants.LIST_SLASH_COMMANDS_USED, "List the frequency with which slash commands are used");
    }

    public void execute(SlashCommandInteractionEvent event) {
        int buttonsPressed = 0;
        int slashCommandsUsed = 0;
        
        Map<String, Game> mapList = GameManager.getInstance().getGameNameToGame();
        HashMap<String, Integer> slashCommands = new HashMap<String, Integer>();    
        for (Game activeGame : mapList.values()) {
            buttonsPressed = activeGame.getButtonPressCount() + buttonsPressed;
            slashCommandsUsed = activeGame.getSlashCommandsRunCount() + slashCommandsUsed;
            for(String command : activeGame.getAllSlashCommandsUsed().keySet()){
                int numUsed = activeGame.getAllSlashCommandsUsed().get(command);
                int numUsed2 = 0;
                if(slashCommands.containsKey(command)){
                    numUsed2 = slashCommands.get(command);
                }
                slashCommands.put(command, numUsed+numUsed2);
            }
        }
        String longMsg = "The number of button pressed so far recorded is "+ buttonsPressed + ". The number of slash commands used is "+ slashCommandsUsed +". The following is their recorded frequency \n";
        List<String> keys = new ArrayList<String>();
        keys.addAll(slashCommands.keySet());
        Collections.sort(keys);
        for(String command : keys){
            longMsg = longMsg + command+ ": "+slashCommands.get(command)+ " \n";
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), longMsg);
    }

    
}
