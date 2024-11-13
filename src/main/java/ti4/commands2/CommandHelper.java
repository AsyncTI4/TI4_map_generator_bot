package ti4.commands2;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.unions.IThreadContainerUnion;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import ti4.AsyncTI4DiscordBot;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.Player;
import ti4.map.UserGameContextManager;
import ti4.message.MessageHelper;

@UtilityClass
public class CommandHelper {

    public static List<Choice> toChoices(String... values) {
        return toChoices(Arrays.asList(values));
    }

    public static List<Choice> toChoices(List<String> values) {
        return values.stream().map(v -> new Choice(v, v)).toList();
    }

    @NotNull
    public static String getGameName(SlashCommandInteraction event) {
        // try to get game name from channel name
        var channel = event.getChannel();
        String gameName = getGameNameFromChannelName(channel.getName());
        if (GameManager.isValidGame(gameName)) {
            return gameName;
        }
        // if a thread, try to get game name from parent
        if (channel instanceof ThreadChannel) {
            IThreadContainerUnion parentChannel = ((ThreadChannel) channel).getParentChannel();
            gameName = getGameNameFromChannelName(parentChannel.getName());
        }
        if (GameManager.isValidGame(gameName)) {
            return gameName;
        }
        return gameName;
    }

    private static String getGameNameFromChannelName(String channelName) {
        String gameName = channelName.replace(Constants.CARDS_INFO_THREAD_PREFIX, "");
        gameName = gameName.replace(Constants.BAG_INFO_THREAD_PREFIX, "");
        gameName = StringUtils.substringBefore(gameName, "-");
        return gameName;
    }

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

    public static String getHeaderText(GenericInteractionCreateEvent event) {
        if (event instanceof SlashCommandInteractionEvent) {
            return " used `" + ((SlashCommandInteractionEvent) event).getCommandString() + "`";
        }
        if (event instanceof ButtonInteractionEvent) {
            return " pressed `" + ((ButtonInteractionEvent) event).getButton().getId() + "`";
        }
        return " used the force";
    }
}
