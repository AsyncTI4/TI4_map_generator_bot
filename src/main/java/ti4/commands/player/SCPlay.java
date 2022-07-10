package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.MapSaveLoadManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class SCPlay extends PlayerSubcommandData {
    public SCPlay() {
        super(Constants.SC_PLAY, "Play SC");
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set stats"));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getPlayer(activeMap, player, event);
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
        message += "Strategy card " + Helper.getSCAsMention(sc) + " played. Please react with your faction symbol to pass on the secondary or post to follow.";
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        String userID = event.getUser().getId();
        Map activeMap = MapManager.getInstance().getUserActiveMap(userID);
        MapSaveLoadManager.saveMap(activeMap);
        MessageHelper.replyToMessageTI4Logo(event);
    }
}
