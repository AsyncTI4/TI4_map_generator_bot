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

public class SCFollow extends PlayerSubcommandData {
    public SCFollow() {
        super(Constants.SC_FOLLOW, "Follow SC");
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set stats"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);
        player = Helper.getPlayer(activeMap, player, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "You're not a player of this game");
            return;
        }
        int strategicCC = player.getStrategicCC();
        if (strategicCC == 0){
            MessageHelper.sendMessageToChannel(event.getChannel(), "Have 0 CC in Strategy, can't follow");
            return;
        }
        strategicCC--;
        player.setStrategicCC(strategicCC);
        String message = Helper.getPlayerRepresentation(event, player) + " following SC, deducted 1 CC from Strategy Tokens";
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
