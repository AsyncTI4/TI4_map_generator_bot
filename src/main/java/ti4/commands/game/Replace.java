package ti4.commands.game;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.MapGenerator;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.MapSaveLoadManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.Collection;

public class Replace extends GameSubcommandData {

    public Replace() {
        super(Constants.REPLACE, "Replace player in game");
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER1, "Removed player @playerName").setRequired(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER2, "Replacement player @playerName").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        User callerUser = event.getUser();

        Map map = getActiveMap();
        Collection<Player> players = map.getPlayers().values();
        if (players.stream().noneMatch(player -> player.getUserID().equals(callerUser.getId())) && !event.getUser().getId().equals(MapGenerator.userID)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Only game players can replace a player.");
            return;
        }
        String message = "";
        OptionMapping removeOption = event.getOption(Constants.PLAYER1);
        OptionMapping addOption = event.getOption(Constants.PLAYER2);
        if (removeOption != null && addOption != null) {
            User removedUser = removeOption.getAsUser();
            User addedUser = addOption.getAsUser();
            if (players.stream().anyMatch(player -> player.getUserID().equals(removedUser.getId())) &&
                    players.stream().noneMatch(player -> player.getUserID().equals(addedUser.getId()))) {
                message = Helper.getGamePing(event, map) + " Player: " + removedUser.getName() + " replaced by player: " + addedUser.getName();
                Player player = map.getPlayer(removedUser.getId());
                player.setUserName(addedUser.getName());
                player.setUserID(addedUser.getId());
            } else {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Specify player that is in game to be removed and player that is not in game to be replacement");
                return;
            }
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Specify player to remove and replacement");
            return;
        }
        MapSaveLoadManager.saveMap(map);
        MapSaveLoadManager.reload(map);
        MessageHelper.replyToMessage(event, message);
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
    }
}