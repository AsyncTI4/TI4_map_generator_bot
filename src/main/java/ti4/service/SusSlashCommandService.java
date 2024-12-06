package ti4.service;

import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.AsyncTI4DiscordBot;
import ti4.helpers.Constants;
import ti4.helpers.ThreadGetter;
import ti4.map.manage.GameManager;
import ti4.map.manage.ManagedGame;
import ti4.message.MessageHelper;
import ti4.service.game.GameNameService;

@UtilityClass
public class SusSlashCommandService {

    private static final List<String> HARMLESS_COMMANDS = List.of(
        Constants.HELP, Constants.STATISTICS, Constants.BOTHELPER, Constants.DEVELOPER, Constants.SEARCH, Constants.USER, Constants.SHOW_GAME,
        Constants.CARDS_INFO, Constants.MILTY, Constants.BUTTON
    );

    private static final List<String> HARMLESS_SUBCOMMANDS = List.of(
        Constants.INFO, Constants.CREATE_GAME_BUTTON, "po_info", Constants.DICE_LUCK, Constants.SHOW_AC_DISCARD_LIST, "show_deck",
        Constants.TURN_STATS, Constants.SHOW_AC_REMAINING_CARD_COUNT, Constants.SHOW_HAND, Constants.SHOW_BAG, Constants.UNIT_INFO,
        Constants.TURN_END, Constants.PING_ACTIVE_PLAYER, Constants.SHOW_ALL_SO_TO_ALL, Constants.END, Constants.REMATCH, Constants.ABILITY_INFO,
        Constants.SPENDS, Constants.CHANGE_COLOR
    );

    private static final List<String> EXCLUDED_GAMES = List.of("pbd1000", "pbd100two");

    public static void checkIfShouldReportSusSlashCommand(SlashCommandInteractionEvent event, String jumpUrl) {
        String gameName = GameNameService.getGameName(event);
        ManagedGame managedGame = GameManager.getManagedGame(gameName);
        if (managedGame == null || managedGame.isFowMode()) return;

        if (HARMLESS_COMMANDS.contains(event.getInteraction().getName())) return;

        if (event.getInteraction().getSubcommandName() != null && HARMLESS_SUBCOMMANDS.contains(event.getInteraction().getSubcommandName())) return;

        if (EXCLUDED_GAMES.contains(managedGame.getName())) return;

        boolean isPrivateThread = event.getMessageChannel() instanceof ThreadChannel thread && !thread.isPublic();
        boolean isPublicThread = event.getMessageChannel() instanceof ThreadChannel thread && thread.isPublic();
        boolean isNotGameChannel = event.getMessageChannel() != managedGame.getActionsChannel()
            && event.getMessageChannel() != managedGame.getTableTalkChannel()
            && !event.getMessageChannel().getName().contains("bot-map-updates");

        if ((isPrivateThread || isNotGameChannel) && !isPublicThread) {
            reportSusSlashCommand(event, jumpUrl);
        }
    }

    private static void reportSusSlashCommand(SlashCommandInteractionEvent event, String jumpUrl) {
        TextChannel bothelperLoungeChannel = AsyncTI4DiscordBot.guildPrimary.getTextChannelsByName("staff-lounge", true).stream()
            .findFirst().orElse(null);
        if (bothelperLoungeChannel == null) return;
        ThreadGetter.getThreadInChannel(bothelperLoungeChannel, "sus-slash-commands", true, true,
            threadChannel -> {
                String sb = event.getUser().getEffectiveName() + " " + "`" + event.getCommandString() + "` " + jumpUrl;
                MessageHelper.sendMessageToChannel(threadChannel, sb);
            });
    }
}
