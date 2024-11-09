package ti4.helpers;

import java.util.List;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.AsyncTI4DiscordBot;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class SlashCommandAcceptanceHelper {

    public static boolean shouldAcceptIfActivePlayerOfGame(String actionId, SlashCommandInteractionEvent event) {
        if (!event.getName().equals(actionId)) {
            return false;
        }
        String userID = event.getUser().getId();
        if (!GameManager.isUserWithActiveGame(userID)) {
            MessageHelper.replyToMessage(event, "Set your active game using: /set_game gameName");
            return false;
        }
        Game userActiveGame = GameManager.getUserActiveGame(userID);
        if (!userActiveGame.getPlayerIDs().contains(userID) && !userActiveGame.isCommunityMode()) {
            MessageHelper.replyToMessage(event, "You're not a player of the game, please call function /join gameName");
            return false;
        }
        return true;
    }

    public static boolean shouldAcceptIfHasRole(String actionId, SlashCommandInteractionEvent event, List<Role> acceptedRoles) {
        if (!event.getName().equals(actionId)) {
            return false;
        }
        Member member = event.getMember();
        if (member == null) {
            return false;
        }
        List<Role> roles = member.getRoles();
        for (Role role : acceptedRoles) {
            if (roles.contains(role)) {
                return true;
            }
        }
        var acceptRolesStr = acceptedRoles.stream().map(Role::getName).collect(Collectors.joining(", "));
        MessageHelper.replyToMessage(event, "You are not authorized to use this command. You must have one of the following roles: " + acceptRolesStr);
        return false;
    }

    public static boolean shouldAcceptIfIsAdminOrIsPartOfGame(String actionId, SlashCommandInteractionEvent event) {
        if (!event.getName().equals(actionId)) {
            return false;
        }
        String userID = event.getUser().getId();
        Member member = event.getMember();
        if (member != null) {
            List<Role> roles = member.getRoles();
            for (Role role : AsyncTI4DiscordBot.adminRoles) {
                if (roles.contains(role)) {
                    return true;
                }
            }
        }
        Game userActiveGame = GameManager.getUserActiveGame(userID);
        if (userActiveGame == null) {
            MessageHelper.replyToMessage(event, "Set your active game using: /set_game gameName");
            return false;
        }
        if (userActiveGame.isCommunityMode()) {
            Player player = Helper.getGamePlayer(userActiveGame, null, event, userID);
            if (player == null || !userActiveGame.getPlayerIDs().contains(player.getUserID()) && !event.getUser().getId().equals(AsyncTI4DiscordBot.userID)) {
                MessageHelper.replyToMessage(event, "You're not a player of the game, please call function /join gameName");
                return false;
            }
        } else if (!userActiveGame.getPlayerIDs().contains(userID) && !event.getUser().getId().equals(AsyncTI4DiscordBot.userID)) {
            MessageHelper.replyToMessage(event, "You're not a player of the game, please call function /join gameName");
            return false;
        }
        if (!event.getChannel().getName().startsWith(userActiveGame.getName() + "-")) {
            MessageHelper.replyToMessage(event, "Commands can be executed only in game specific channels");
            return false;
        }
        return true;
    }
}
