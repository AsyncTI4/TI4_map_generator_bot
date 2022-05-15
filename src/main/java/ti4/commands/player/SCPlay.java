package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class SCPlay extends PlayerSubcommandData {
    public SCPlay() {
        super(Constants.SC_PLAY, "Play SC");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Your not a player of this game");
            return;
        }
        int sc = player.getSC();
        if (sc == 0) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No SC selected by player");
            return;
        }
        Boolean isSCPlayed = activeMap.getScPlayed().get(sc);
        if (isSCPlayed != null && isSCPlayed) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "SC already played");
            return;
        }
        activeMap.setSCPlayed(sc, true);
        String categoryForPlayers = Helper.getGamePing(event, activeMap);
        String message = "";
        if (!categoryForPlayers.isEmpty()) {
            message += categoryForPlayers + "\n";
        }
        message += "Strategy card " + Helper.getSCAsMention(sc) + " played. Please react with your faction symbol to pass on the secondary or post it to follow.";
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
    }
}
