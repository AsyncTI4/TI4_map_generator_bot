package ti4.service;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.ParentCommand;
import ti4.commands.SlashCommandManager;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.ManagedGame;
import ti4.message.MessageHelper;
import ti4.service.fow.GMService;
import ti4.service.game.GameNameService;
import ti4.spring.jda.JdaService;

@UtilityClass
public class SusSlashCommandService {

    private static final List<String> EXCLUDED_GAMES = List.of("pbd1000", "pbd100two");

    public static void checkIfShouldReportSusSlashCommand(SlashCommandInteractionEvent event, String jumpUrl) {
        String gameName = GameNameService.getGameName(event);
        ManagedGame managedGame = GameManager.getManagedGame(gameName);
        if (managedGame == null) return;

        ParentCommand command =
                SlashCommandManager.getCommand(event.getInteraction().getName());
        if (command == null || !command.isSuspicious(event)) return;

        if (EXCLUDED_GAMES.contains(managedGame.getName())) return;

        boolean isPrivateThread = event.getMessageChannel() instanceof ThreadChannel thread && !thread.isPublic();
        boolean isPublicThread = event.getMessageChannel() instanceof ThreadChannel thread && thread.isPublic();
        boolean isNotGameChannel = event.getMessageChannel() != managedGame.getActionsChannel()
                && event.getMessageChannel() != managedGame.getTableTalkChannel()
                && !event.getMessageChannel().getName().contains("bot-map-updates");
        boolean isSinglePlayerGame = managedGame.getRealPlayers().size() <= 1;

        if (isReplaceOrLeaveCommand(event)) {
            reportToModerationLog(event, jumpUrl);
        } else if (!managedGame.isFowMode()
                && !isSinglePlayerGame
                && (isPrivateThread || isNotGameChannel)
                && !isPublicThread) {
            reportToSusSlashCommandLog(event, jumpUrl, gameName);
            String sb = event.getUser().getEffectiveName() + " privately used the command: " + "`"
                    + event.getFullCommandName() + "`";
            MessageHelper.sendMessageToChannel(managedGame.getMainGameChannel(), sb);
            String sb2 = event.getUser().getAsMention()
                    + " this is a reminder that you should use most commands that alter the game state in the game channels, not in private threads. This is so that all players can track the game state accurately. Your command has been reported to the admins, but no action will be taken unless it's determined to be a violation.";
            MessageHelper.sendMessageToChannel(event.getChannel(), sb2);
        }

        if (managedGame.isFowMode()) {
            Game game = managedGame.getGame();
            Player player = game.getPlayer(event.getUser().getId());
            if (player != null && !game.getPlayersWithGMRole().contains(player)) {
                GMService.logPlayerActivity(
                        game,
                        player,
                        event.getUser().getEffectiveName() + " " + "`" + event.getCommandString() + "`",
                        jumpUrl,
                        false);
            }
        }
    }

    private static boolean isReplaceOrLeaveCommand(SlashCommandInteractionEvent event) {
        return event.getInteraction().getSubcommandName() != null
                && ("replace".equalsIgnoreCase(event.getInteraction().getSubcommandName())
                        || "leave".equalsIgnoreCase(event.getInteraction().getSubcommandName()));
    }

    public static void reportToModerationLog(SlashCommandInteractionEvent event, String jumpUrl) {
        TextChannel moderationLogChannel =
                JdaService.guildPrimary.getTextChannelsByName("moderation-log", true).stream()
                        .findFirst()
                        .orElse(null);
        if (moderationLogChannel == null) return;
        String message = event.getUser().getEffectiveName() + " " + "`" + event.getCommandString() + "` " + jumpUrl;
        MessageHelper.sendMessageToChannel(moderationLogChannel, message);
    }

    private static void reportToSusSlashCommandLog(
            SlashCommandInteractionEvent event, String jumpUrl, String gameName) {
        TextChannel moderationLogChannel =
                JdaService.guildPrimary.getTextChannelsByName("sus-slash-commands-log", true).stream()
                        .findFirst()
                        .orElse(null);
        if (moderationLogChannel == null) return;
        String message = event.getUser().getEffectiveName() + " (" + gameName + ") `" + event.getCommandString() + "` "
                + jumpUrl;
        MessageHelper.sendMessageToChannel(moderationLogChannel, message);
    }
}
