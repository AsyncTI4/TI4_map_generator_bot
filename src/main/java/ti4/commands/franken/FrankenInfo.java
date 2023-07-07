package ti4.commands.franken;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class FrankenInfo extends FrankenSubcommandData {
    public FrankenInfo() {
        super(Constants.INFO, "Send the game's Franken Info to a thread for all players to see");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        String threadName = activeMap.getName() + " - Franken Info";
        MessageHelper.sendMessageToThread(event.getChannel(), threadName, getFrankenInfo(activeMap));
    }

    public static String getFrankenInfo(Map activeMap) {
        StringBuilder sb = new StringBuilder();
        sb.append("# __Franken Info for ").append(activeMap.getName()).append("__\n");

        for (Player player : activeMap.getRealPlayers()) {
            sb.append("## ").append(player.getUserName()).append("\n");
            //stuff for each player
        }
        
        return "";
    }

    
}
