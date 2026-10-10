package ti4.discord.interactions.commands.game;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.JdaService;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.TIGLHelper;
import ti4.helpers.TIGLHelper.TIGLRank;
import ti4.message.MessageHelper;

@UtilityClass
public class ReplaceRankButtonHandler {

    private static final String CONFIRM_PREFIX = "tiglReplaceLowerRank_";

    public static void askToLowerGameRank(
            MessageChannel channel, Game game, Player replacedPlayer, User replacementUser, TIGLRank incomingRank) {
        TIGLRank gameRank = game.getMinimumTIGLRankAtGameStart();

        String message = "## " + ti4.service.emoji.MiscEmojis.TIGL + "This replacement would lower the game's rank\n"
                + replacementUser.getName() + " was **" + incomingRank.getShortName()
                + "** when this game started, but the game is ranked **" + gameRank.getShortName() + "**.\n"
                + "Adding them will re-rank this game to **" + incomingRank.getShortName()
                + "** for the league. Continue?\n"
                + "-# Only players seated in this game can press these buttons. A Bothelper who is not seated needs"
                + " to `/game join` first.";

        String buttonId = CONFIRM_PREFIX + replacedPlayer.getUserID() + "_" + replacementUser.getId();
        MessageHelper.sendMessageToChannelWithButtons(
                channel,
                message,
                List.of(
                        Buttons.red(buttonId, "Yes - lower the game to " + incomingRank.getShortName()),
                        Buttons.CANCEL));
    }

    public static void warnRankUnverified(MessageChannel channel, Game game, User replacementUser) {
        TIGLRank gameRank = game.getMinimumTIGLRankAtGameStart();
        String required = gameRank == null ? "an unknown rank" : "**" + gameRank.getShortName() + "**";

        MessageHelper.sendMessageToChannel(
                channel,
                "⚠️ Could not reach the TIGL league to check " + replacementUser.getName() + "'s rank, so the"
                        + " replacement went ahead unverified.\n> This game requires " + required
                        + " at game start (" + Helper.getDateRepresentation(game.getCreationDateTime())
                        + ") - please check for yourselves that they qualify, and run `/tigl init_ranks` once the"
                        + " league is reachable again.");
    }

    @ButtonHandler(CONFIRM_PREFIX)
    public static void confirmLowerGameRank(ButtonInteractionEvent event, Game game, String buttonID) {
        if (game == null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "This button only works in the game's own channels. Run `/game replace` there instead.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        String remainder = StringUtils.substringAfter(buttonID, CONFIRM_PREFIX);
        String replacedUserId = StringUtils.substringBeforeLast(remainder, "_");
        String replacementUserId = StringUtils.substringAfterLast(remainder, "_");

        Player replacedPlayer = game.getPlayer(replacedUserId);
        User replacementUser = JdaService.jda.getUserById(replacementUserId);
        if (replacedPlayer == null
                || !replacedPlayer.isRealPlayer()
                || replacementUser == null
                || isAlreadySeated(game, replacementUserId)) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Could not resolve this replacement any more. Run the command again.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        Guild guild = game.getGuild() == null ? event.getGuild() : game.getGuild();
        Member newMember = guild == null ? null : guild.getMemberById(replacementUserId);
        if (newMember == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Added player must be on the game's server.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        ButtonHelper.deleteMessage(event);
        Replace.performReplacement(game, replacedPlayer, replacementUser, guild, newMember, event.getMessageChannel());
        TIGLHelper.initializeRanksAsync(game, event.getMessageChannel());
    }

    private static boolean isAlreadySeated(Game game, String userId) {
        Player seated = game.getPlayer(userId);
        return seated != null && seated.isRealPlayer();
    }
}
