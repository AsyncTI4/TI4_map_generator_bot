package ti4.commands.game;

import java.util.Map;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.AsyncTI4DiscordBot;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

public class Replace extends GameSubcommandData {

    public Replace() {
        super(Constants.REPLACE, "Replace player in game");
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player being replaced @playerName").setRequired(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER2, "Replacement player @playerName").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "Game name"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        User callerUser = event.getUser();

        Game activeGame = getActiveGame();
        Collection<Player> players = activeGame.getPlayers().values();
        Member member = event.getMember();
        boolean isAdmin = false;
        if (member != null) {
            List<Role> roles = member.getRoles();
            for (Role role : AsyncTI4DiscordBot.bothelperRoles) {
                if (roles.contains(role)) {
                    isAdmin = true;
                    break;
                }
            }
        }
        if (players.stream().noneMatch(player -> player.getUserID().equals(callerUser.getId())) && !isAdmin) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Only game players or Bothelpers can replace a player.");
            return;
        }
        
        OptionMapping removeOption = event.getOption(Constants.PLAYER);
        OptionMapping addOption = event.getOption(Constants.PLAYER2);
        if (removeOption == null || addOption == null) {
            MessageHelper.replyToMessage(event, "Specify player to remove and replacement");
            return;
        }
        
        Player removedPlayer = Helper.getPlayer(activeGame, null, event);
        if (removedPlayer == null){
            MessageHelper.replyToMessage(event, "Could not find player for faction/color to replace");
            return;
        }
        User addedUser = addOption.getAsUser();
        boolean notRealPlayer = players.stream().noneMatch(player -> player.getUserID().equals(addedUser.getId()));
        if (!notRealPlayer) {
            if (activeGame.getPlayer(addedUser.getId()).getFaction() == null) {
                activeGame.removePlayer(addedUser.getId());
            }
        }
        
        //REMOVE ROLE
        Guild guild = event.getGuild();
        Member removedMember = guild.getMemberById(removedPlayer.getUserID());
        List<Role> roles = guild.getRolesByName(activeGame.getName(), true);
        if (removedMember != null && roles.size() == 1) {
            guild.removeRoleFromMember(removedMember, roles.get(0)).queue();
        }
        
        //ADD ROLE
        Member addedMember = guild.getMemberById(addedUser.getId());
        if (roles.size() == 1) {
            guild.addRoleToMember(addedMember, roles.get(0)).queue();
        }
        
        String message;
        if (players.stream().noneMatch(player -> player.getUserID().equals(removedPlayer.getUserID())) || players.stream().anyMatch(player -> player.getUserID().equals(addedUser.getId()))) {
            MessageHelper.replyToMessage(event, "Specify player that is in game to be removed and player that is not in game to be replacement");
            return;
        }
            
        message = "Game: " + activeGame.getName() + "  Player: " + removedPlayer.getUserName() + " replaced by player: " + addedUser.getName();
        Player player = activeGame.getPlayer(removedPlayer.getUserID());
        LinkedHashMap<String, List<String>> scoredPublicObjectives = activeGame.getScoredPublicObjectives();
        for (Map.Entry<String, List<String>> poEntry : scoredPublicObjectives.entrySet()) {
            List<String> value = poEntry.getValue();
            boolean removed = value.remove(removedPlayer.getUserID());
            if (removed){
                value.add(addedUser.getId());
            }
        }
        player.setUserName(addedUser.getName());
        player.setUserID(addedUser.getId());
        player.setTotalTurnTime(0);
        player.setNumberTurns(0);
        if (removedPlayer.getUserID().equals(activeGame.getSpeaker())) {
            activeGame.setSpeaker(addedUser.getId());
        }
        if (removedPlayer.getUserID().equals(activeGame.getActivePlayer())) {
            // do not update stats for this action
            activeGame.setActivePlayer(addedUser.getId());
        }

        Helper.fixGameChannelPermissions(event.getGuild(), activeGame);
        GameSaveLoadManager.saveMap(activeGame, event);
        GameSaveLoadManager.reload(activeGame);
        if (FoWHelper.isPrivateGame(activeGame)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), message);
        } else {
            MessageHelper.sendMessageToChannel(activeGame.getActionsChannel(), message);
        }
    }
}