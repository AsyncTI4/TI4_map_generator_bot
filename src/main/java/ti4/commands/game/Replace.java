package ti4.commands.game;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.AsyncTI4DiscordBot;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Replace extends GameSubcommandData {

    public Replace() {
        super(Constants.REPLACE, "Replace player in game");
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player being replaced @playerName").setRequired(false));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction being replaced").setRequired(false).setAutoComplete(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER2, "Replacement player @playerName").setRequired(false));
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "Game name").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        User callerUser = event.getUser();

        Game game = getActiveGame();
        Collection<Player> players = game.getPlayers().values();
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

        OptionMapping removeOption = event.getOption(Constants.FACTION_COLOR);
        OptionMapping removePlayerOption = event.getOption(Constants.PLAYER);
        OptionMapping addOption = event.getOption(Constants.PLAYER2);
        if ((removeOption == null && removePlayerOption == null) || addOption == null) {
            MessageHelper.replyToMessage(event, "Specify player to remove and replacement");
            return;
        }

        Player removedPlayer = Helper.getPlayer(game, null, event);
        if (removedPlayer == null || (removePlayerOption == null && removedPlayer.getFaction() == null)) {
            MessageHelper.replyToMessage(event, "Could not find faction/color to replace");
            return;
        }
        String removedPlayerID = removedPlayer.getUserID();

        boolean isNullFaction = removedPlayer.getFaction() == null || removedPlayer.getFaction().equals("null");
        if (removePlayerOption == null && isNullFaction) {
            MessageHelper.replyToMessage(event, "Cannot determine player if they are not set up. Specify `player` option instead.");
            return;
        }

        User addedUser = addOption.getAsUser();
        boolean notRealPlayer = players.stream().noneMatch(player -> player.getUserID().equals(addedUser.getId()));
        if (!notRealPlayer) {
            if (game.getPlayer(addedUser.getId()).getFaction() == null) {
                game.removePlayer(addedUser.getId());
            }
        }

        //REMOVE ROLE
        Guild guild = game.getGuild();
        Member removedMember = guild.getMemberById(removedPlayer.getUserID());
        List<Role> roles = guild.getRolesByName(game.getName(), true);
        if (removedMember != null && roles.size() == 1) {
            guild.removeRoleFromMember(removedMember, roles.getFirst()).queue();
        }

        //ADD ROLE
        Member addedMember = guild.getMemberById(addedUser.getId());
        if (addedMember == null) return;

        if (roles.size() == 1) {
            guild.addRoleToMember(addedMember, roles.getFirst()).queue();
        }

        String message;
        if (players.stream().noneMatch(player -> player.getUserID().equals(removedPlayer.getUserID()))) {
            MessageHelper.replyToMessage(event, "Specify player that is in game to be removed");
            return;
        }
        if (players.stream().anyMatch(player -> player.getUserID().equals(addedUser.getId()))) {
            MessageHelper.replyToMessage(event, "Specify player that is **__not__** in the game to be the replacement");
            return;
        }

        message = "Game: " + game.getName() + "  Player: " + removedPlayer.getUserName() + " replaced by player: " + addedUser.getName();
        Player player = game.getPlayer(removedPlayer.getUserID());
        boolean speaker = player.isSpeaker();
        Map<String, List<String>> scoredPublicObjectives = game.getScoredPublicObjectives();
        for (Map.Entry<String, List<String>> poEntry : scoredPublicObjectives.entrySet()) {
            List<String> value = poEntry.getValue();
            boolean removed = value.remove(removedPlayer.getUserID());
            if (removed) {
                value.add(addedUser.getId());
            }
        }

        player.setUserName(addedUser.getName());
        player.setUserID(addedUser.getId());
        player.setTotalTurnTime(0);
        player.setNumberTurns(0);
        if (removedPlayer.getUserID().equals(game.getSpeakerUserID())) {
            game.setSpeakerUserID(addedUser.getId());
        }
        if (removedPlayer.getUserID().equals(game.getActivePlayerID())) {
            // do not update stats for this action
            game.setActivePlayerID(addedUser.getId());
        }

        Helper.fixGameChannelPermissions(event.getGuild(), game);
        ThreadChannel mapThread = game.getBotMapUpdatesThread();
        if (mapThread != null && !mapThread.isLocked()) {
            mapThread.getManager().setArchived(false).queue(success -> mapThread.addThreadMember(addedMember).queueAfter(5, TimeUnit.SECONDS), BotLogger::catchRestError);
        }
        game.getMiltyDraftManager().replacePlayer(game, removedPlayerID, player.getUserID());

        if (speaker) {
            game.setSpeakerUserID(player.getUserID());
        }
        GameSaveLoadManager.saveGame(game, event);
        GameSaveLoadManager.reload(game);

        // Load the new game instance so that we can repost the milty draft
        game = GameManager.getGame(game.getName());
        if (game.getMiltyDraftManager().getDraftIndex() < game.getMiltyDraftManager().getDraftOrder().size()) {
            game.getMiltyDraftManager().repostDraftInformation(game);
        }

        if (FoWHelper.isPrivateGame(game)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), message);
        } else {
            MessageHelper.sendMessageToChannel(game.getActionsChannel(), message);
        }

    }
}
