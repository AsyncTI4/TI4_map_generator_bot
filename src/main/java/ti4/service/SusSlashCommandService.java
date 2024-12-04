package ti4.service;

import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.AsyncTI4DiscordBot;
import ti4.helpers.Constants;
import ti4.helpers.ThreadGetter;
import ti4.map.Game;
import ti4.message.MessageHelper;

@UtilityClass
public class SusSlashCommandService {

    public static void checkIfShouldReportSusSlashCommand(SlashCommandInteractionEvent event, Game game) {
        if (game == null) return;
        if (game.isFowMode()) return;

        final List<String> harmlessCommands = List.of(Constants.HELP, Constants.STATISTICS, Constants.BOTHELPER, Constants.DEVELOPER, Constants.SEARCH, Constants.USER, Constants.SHOW_GAME);
        if (harmlessCommands.contains(event.getInteraction().getName())) return;

        final List<String> harmlessSubcommands = List.of(Constants.CREATE_GAME_BUTTON);
        if (event.getInteraction().getSubcommandName() != null && harmlessSubcommands.contains(event.getInteraction().getSubcommandName())) return;

        final List<String> harmlessCommandOptions = List.of(Constants.GAME_NAME);
        if (harmlessCommandOptions.stream().anyMatch(cmd -> event.getOption(cmd) != null)) return;

        final List<String> excludedGames = List.of("pbd1000", "pbd100two");
        if (excludedGames.contains(game.getName())) return;

        boolean isPrivateThread = event.getMessageChannel() instanceof ThreadChannel thread && !thread.isPublic();
        boolean isPublicThread = event.getMessageChannel() instanceof ThreadChannel thread && thread.isPublic();
        boolean isNotGameChannel = event.getMessageChannel() != game.getActionsChannel()
            && event.getMessageChannel() != game.getTableTalkChannel()
            && !event.getMessageChannel().getName().contains("bot-map-updates");

        if ((isPrivateThread || isNotGameChannel) && !isPublicThread) {
            reportSusSlashCommand(event);
        }
    }

    private static void reportSusSlashCommand(SlashCommandInteractionEvent event) {
        TextChannel bothelperLoungeChannel = AsyncTI4DiscordBot.guildPrimary.getTextChannelsByName("staff-lounge", true).stream().findFirst().orElse(null);
        if (bothelperLoungeChannel == null) return;
        ThreadGetter.getThreadInChannel(bothelperLoungeChannel, "sus-slash-commands", true, true,
            threadChannel -> {
                String sb = event.getUser().getEffectiveName() + " " + "`" + event.getCommandString() + "` " + threadChannel.getJumpUrl();
                MessageHelper.sendMessageToChannel(threadChannel, sb);
            });
    }
}
