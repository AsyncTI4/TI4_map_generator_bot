package ti4.commands.franken;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;

public class FrankenInfo extends FrankenSubcommandData {
    public FrankenInfo() {
        super(Constants.INFO, "Send the game's Franken Info to a thread for all players to see");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        //Game activeGame = getActiveGame();
        //String threadName = activeGame.getName() + " - Franken Info";
        //MessageHelper.sendMessageToThread(event.getChannel(), threadName, getFrankenInfo(activeGame));
    }

//    public static String getFrankenInfo(Game activeGame) {
//        StringBuilder sb = new StringBuilder();
//        sb.append("# __Franken Info for ").append(activeGame.getName()).append("__\n");
//
//        for (Player player : activeGame.getRealPlayers()) {
//            sb.append("## ").append(player.getUserName()).append("\n");
//            //stuff for each player
//        }
//
//        return "";
    //}
}
