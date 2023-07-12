package ti4.commands.game;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.MapGenerator;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapSaveLoadManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

public class Replace extends GameSubcommandData {

    public Replace() {
        super(Constants.REPLACE, "Replace player in game");
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Replace player in Faction/Color ").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER2, "Replacement player @playerName").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        User callerUser = event.getUser();

        Map activeMap = getActiveMap();
        Collection<Player> players = activeMap.getPlayers().values();
        Member member = event.getMember();
        boolean isAdmin = false;
        if (member != null) {
            java.util.List<Role> roles = member.getRoles();
            for (Role role : MapGenerator.adminRoles) {
                if (roles.contains(role)) {
                    isAdmin = true;
                }
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
            Player removedPlayer = Helper.getPlayer(activeMap, null, event);
            if (removedPlayer == null){
                MessageHelper.replyToMessage(event, "Could not find player for faction/color to replace");
                return;
            }
            User addedUser = addOption.getAsUser();
            boolean notRealPlayer = players.stream().noneMatch(player -> player.getUserID().equals(addedUser.getId()));
            if (!notRealPlayer) {
                if (activeMap.getPlayer(addedUser.getId()).getFaction() == null) {
                    activeMap.removePlayer(addedUser.getId());
                }
            }

            //REMOVE ROLE
            Guild guild = event.getGuild();
            Member removedMember = guild.getMemberById(removedPlayer.getUserID());
            List<Role> roles = guild.getRolesByName(activeMap.getName(), true);
            if (removedMember != null && roles != null && roles.size() == 1) {
                guild.removeRoleFromMember(removedMember, roles.get(0)).queue();
            }

            Member addedMember = guild.getMemberById(addedUser.getId());
            if (addedUser != null && roles != null && roles.size() == 1) {
                guild.addRoleToMember(addedMember, roles.get(0)).queue();
            }

            if (players.stream().anyMatch(player -> player.getUserID().equals(removedPlayer.getUserID())) &&
            players.stream().noneMatch(player -> player.getUserID().equals(addedUser.getId()))) {
                message = Helper.getGamePing(event, activeMap) + " Player: " + removedPlayer.getUserName() + " replaced by player: " + addedUser.getName();
                Player player = activeMap.getPlayer(removedPlayer.getUserID());
                LinkedHashMap<String, List<String>> scoredPublicObjectives = activeMap.getScoredPublicObjectives();
                for (java.util.Map.Entry<String, List<String>> poEntry : scoredPublicObjectives.entrySet()) {
                    List<String> value = poEntry.getValue();
                    boolean removed = value.remove(removedPlayer.getUserID());
                    if (removed){
                        value.add(addedUser.getId());
                    }
                }
                player.setUserName(addedUser.getName());
                player.setUserID(addedUser.getId());
                if (removedPlayer.getUserID().equals(activeMap.getSpeaker())) {
                    activeMap.setSpeaker(addedUser.getId());
                }
                if (removedPlayer.getUserID().equals(activeMap.getActivePlayer())) {
                    // do not update stats for this action
                    activeMap.setActivePlayer(addedUser.getId());
                }
            } else {
                MessageHelper.replyToMessage(event, "Specify player that is in game to be removed and player that is not in game to be replacement");
                return;
            }
        } else {
            MessageHelper.replyToMessage(event, "Specify player to remove and replacement");
            return;
        }
        Helper.fixGameChannelPermissions(event.getGuild(), activeMap);
        MapSaveLoadManager.saveMap(activeMap, event);
        MapSaveLoadManager.reload(activeMap);
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
    }
}