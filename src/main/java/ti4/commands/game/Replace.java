package ti4.commands.game;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.MapGenerator;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.MapSaveLoadManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

public class Replace extends GameSubcommandData {

    public Replace() {
        super(Constants.REPLACE, "Replace player in game");
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Replace player in Faction/Color ").setRequired(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER2, "Replacement player @playerName").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        User callerUser = event.getUser();

        Map map = getActiveMap();
        Collection<Player> players = map.getPlayers().values();
        Member member = event.getMember();
        boolean isAdmin = false;
        if (member != null) {
            java.util.List<Role> roles = member.getRoles();
            if (roles.contains(MapGenerator.adminRole)) {
                isAdmin = true;
            }
        }
        if (players.stream().noneMatch(player -> player.getUserID().equals(callerUser.getId())) && !isAdmin) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Only game players can replace a player.");
            return;
        }
        String message = "";
        OptionMapping removeOption = event.getOption(Constants.FACTION_COLOR);
        OptionMapping addOption = event.getOption(Constants.PLAYER2);
        if (removeOption != null && addOption != null) {
            Player removedPlayer = Helper.getPlayer(map, null, event);
            if (removedPlayer == null){
                MessageHelper.replyToMessage(event, "Could not find player for faction/color to replace");
                return;
            }
            User addedUser = addOption.getAsUser();
            if (players.stream().anyMatch(player -> player.getUserID().equals(removedPlayer.getUserID())) &&
                    players.stream().noneMatch(player -> player.getUserID().equals(addedUser.getId()))) {
                message = Helper.getGamePing(event, map) + " Player: " + removedPlayer.getUserName() + " replaced by player: " + addedUser.getName();
                Player player = map.getPlayer(removedPlayer.getUserID());
                LinkedHashMap<String, List<String>> scoredPublicObjectives = map.getScoredPublicObjectives();
                for (java.util.Map.Entry<String, List<String>> poEntry : scoredPublicObjectives.entrySet()) {
                    List<String> value = poEntry.getValue();
                    boolean removed = value.remove(removedPlayer.getUserID());
                    if (removed){
                        value.add(addedUser.getId());
                    }
                }
                player.setUserName(addedUser.getName());
                player.setUserID(addedUser.getId());
                if (map.getSpeaker().equals(removedPlayer.getUserID())) {
                    map.setSpeaker(addedUser.getId());
                }
                if (map.getActivePlayer().equals(removedPlayer.getUserID())) {
                    // do not update stats for this action
                    map.setActivePlayer(addedUser.getId());
                }
            } else {
                MessageHelper.replyToMessage(event, "Specify player that is in game to be removed and player that is not in game to be replacement");
                return;
            }
        } else {
            MessageHelper.replyToMessage(event, "Specify player to remove and replacement");
            return;
        }
        MapSaveLoadManager.saveMap(map);
        MapSaveLoadManager.reload(map);
        MessageHelper.replyToMessage(event, message);
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
    }
}