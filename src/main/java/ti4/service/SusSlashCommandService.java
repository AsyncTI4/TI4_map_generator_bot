package ti4.service;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.CommandManager;
import ti4.commands.ParentCommand;
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

        ParentCommand command = CommandManager.getCommand(event.getInteraction().getName());
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
            reportToSusSlashCommandLog(event, jumpUrl);
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
        String sb = event.getUser().getEffectiveName() + " " + "`" + event.getCommandString() + "` " + jumpUrl;
        MessageHelper.sendMessageToChannel(moderationLogChannel, sb);
    }

    private static void reportToSusSlashCommandLog(SlashCommandInteractionEvent event, String jumpUrl) {
        TextChannel moderationLogChannel =
                JdaService.guildPrimary.getTextChannelsByName("sus-slash-commands-log", true).stream()
                        .findFirst()
                        .orElse(null);
        if (moderationLogChannel == null) return;
        String sb = event.getUser().getEffectiveName() + " " + "`" + event.getCommandString() + "` " + jumpUrl;
        MessageHelper.sendMessageToChannel(moderationLogChannel, sb);
    }
}
