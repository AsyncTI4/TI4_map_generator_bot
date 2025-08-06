package ti4.service.game;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.persistence.GameManager;

@UtilityClass
public class CloneGameService {

    public static void cloneGame(Game game) {
        String gameName = game.getName();
        String cloneName = gameName + "clone";
        Guild guild = game.getGuild();
        String gameFunName = game.getCustomName();
        String newChatChannelName = cloneName + "-" + gameFunName;
        String newActionsChannelName = cloneName + Constants.ACTIONS_CHANNEL_SUFFIX;
        String newBotThreadName = cloneName + Constants.BOT_CHANNEL_SUFFIX;

        long permission = Permission.MESSAGE_MANAGE.getRawValue() | Permission.VIEW_CHANNEL.getRawValue();

        Role gameRole = guild.createRole()
            .setName(cloneName)
            .setMentionable(true)
            .complete();
        for (Player player : game.getRealPlayers()) {
            Member member = player.getMember();
            if (member != null) {
                guild.addRoleToMember(member, gameRole).complete();
            }
        }
        Category category = game.getMainGameChannel().getParentCategory();
        long gameRoleID = gameRole.getIdLong();
        // CREATE TABLETALK CHANNEL
        TextChannel chatChannel = guild.createTextChannel(newChatChannelName, category)
            .syncPermissionOverrides()
            .addRolePermissionOverride(gameRoleID, permission, 0)
            .complete();

        // CREATE ACTIONS CHANNEL
        TextChannel actionsChannel = guild.createTextChannel(newActionsChannelName, category)
            .syncPermissionOverrides()
            .addRolePermissionOverride(gameRoleID, permission, 0)
            .complete();

        game.setTableTalkChannelID(chatChannel.getId());
        game.setMainChannelID(actionsChannel.getId());
        game.setName(cloneName);
        game.shuffleDecks();
        // CREATE BOT/MAP THREAD
        ThreadChannel botThread = actionsChannel.createThreadChannel(newBotThreadName)
            .complete();
        game.setBotMapUpdatesThreadID(botThread.getId());
        for (Player player : game.getRealPlayers()) {
            player.setCardsInfoThreadID(null);
        }

        GameManager.save(game, "Cloned"); // save the cloned game
        GameManager.reload(gameName); // reload the old game
    }
}
