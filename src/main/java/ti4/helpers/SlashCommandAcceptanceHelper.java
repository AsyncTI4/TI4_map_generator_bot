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
import ti4.map.UserGameContextManager;
import ti4.message.MessageHelper;

public class SlashCommandAcceptanceHelper {

    public static boolean acceptIfPlayerInGame(SlashCommandInteractionEvent event) {
        String userId = event.getUser().getId();
        if (!UserGameContextManager.doesUserHaveContextGame(userId)) {
            MessageHelper.replyToMessage(event, "Set your active game using: /set_game gameName");
            return false;
        }
        String userActiveGameName = UserGameContextManager.getContextGame(userId);
        Game userActiveGame = GameManager.getGame(userActiveGameName);
        if (userActiveGame.isCommunityMode()) {
            Player player = Helper.getPlayerFromGame(userActiveGame, event, userId);
            if (player == null || !userActiveGame.getPlayerIDs().contains(player.getUserID()) && !event.getUser().getId().equals(AsyncTI4DiscordBot.userID)) {
                MessageHelper.replyToMessage(event, "You're not a player of the game, please call function /join gameName");
                return false;
            }
        } else if (!userActiveGame.getPlayerIDs().contains(userId) && !event.getUser().getId().equals(AsyncTI4DiscordBot.userID)) {
            MessageHelper.replyToMessage(event, "You're not a player of the game, please call function /join gameName");
            return false;
        }
        if (!event.getChannel().getName().startsWith(userActiveGame.getName() + "-")) {
            MessageHelper.replyToMessage(event, "Commands can be executed only in game specific channels");
            return false;
        }
        return true;
    }

    public static boolean acceptIfHasRoles(SlashCommandInteractionEvent event, List<Role> acceptedRoles) {
        if (hasRole(event, acceptedRoles)) {
            return true;
        }
        var acceptRolesStr = acceptedRoles.stream().map(Role::getName).collect(Collectors.joining(", "));
        MessageHelper.replyToMessage(event, "You are not authorized to use this command. You must have one of the following roles: " + acceptRolesStr);
        return false;
    }

    private static boolean hasRole(SlashCommandInteractionEvent event, List<Role> acceptedRoles) {
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
        return false;
    }
}
