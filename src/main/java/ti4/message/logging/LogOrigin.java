package ti4.message.logging;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Getter;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import ti4.AsyncTI4DiscordBot;
import ti4.helpers.ButtonHelper;
import ti4.helpers.DateTimeHelper;
import ti4.listeners.ModalListener;
import ti4.map.Game;
import ti4.map.Player;
import ti4.selections.SelectionMenuProcessor;

@Getter
public class LogOrigin {

    @Nullable private final String guildId;
    @Nullable private final String eventString;
    @Nullable private final String gameInfo;
    private final String originTime;

    public LogOrigin(@Nonnull Guild guild) {
        guildId = guild.getId();
        eventString = null;
        gameInfo = null;
        originTime = DateTimeHelper.getCurrentTimestamp();
    }

    public LogOrigin(@Nonnull GuildChannel channel) {
        guildId = channel.getGuild().getId();
        eventString = null;
        gameInfo = null;
        originTime = DateTimeHelper.getCurrentTimestamp();
    }

    public LogOrigin(@Nonnull GenericInteractionCreateEvent event) {
        guildId = event.isFromGuild() ? event.getGuild().getId() : null;
        if (!event.isFromGuild()) {
            BotLogger.warning(
                    "LocationSource created from non-guild event. This will not attribute messages.");
        }
        eventString = buildEventString(event);
        gameInfo = null;
        originTime = DateTimeHelper.getCurrentTimestamp();
    }

    public LogOrigin(@Nonnull Game game) {
        guildId = game.getGuild() != null ? game.getGuild().getId() : null;
        eventString = null;
        gameInfo = buildGameInfo(game);
        originTime = DateTimeHelper.getCurrentTimestamp();
    }

    public LogOrigin(@Nullable Player player) {
        Game g = player != null ? player.getGame() : null;
        if (g == null) {
            BotLogger.warning(
                    "LocationSource created from player with null game. This will not attribute messages.");
        }
        guildId = g != null && g.getGuild() != null ? g.getGuild().getId() : null;
        eventString = null;
        gameInfo = g != null ? buildGameInfo(g) : null;
        originTime = DateTimeHelper.getCurrentTimestamp();
    }

    public LogOrigin(@Nullable GenericInteractionCreateEvent event, @Nonnull Game game) {
        guildId =
                event != null && event.isFromGuild()
                        ? event.getGuild().getId()
                        : game.getGuild() != null ? game.getGuild().getId() : null;
        eventString = event != null ? buildEventString(event) : null;
        gameInfo = buildGameInfo(game);
        originTime = DateTimeHelper.getCurrentTimestamp();
    }

    public LogOrigin(@Nullable GenericInteractionCreateEvent event, @Nonnull Player player) {
        Game g = player.getGame();
        guildId =
                event != null && event.isFromGuild()
                        ? event.getGuild().getId()
                        : g != null && g.getGuild() != null ? g.getGuild().getId() : null;
        eventString = event != null ? buildEventString(event) : null;
        gameInfo = g != null ? buildGameInfo(g) : null;
        originTime = DateTimeHelper.getCurrentTimestamp();
    }

    @Nullable
    private static String buildGameInfo(@Nonnull Game game) {
        return "\nGame info: " + game.gameJumpLinks();
    }

    @Nullable
    private static String buildEventString(@Nonnull GenericInteractionCreateEvent event) {
        StringBuilder builder =
                new StringBuilder().append(event.getUser().getEffectiveName()).append(" ");

        switch (event) {
            case SlashCommandInteractionEvent sEvent ->
                builder.append("used command `")
                        .append(sEvent.getCommandString())
                        .append("`\n");
            case ButtonInteractionEvent bEvent ->
                builder.append("pressed button ")
                        .append(ButtonHelper.getButtonRepresentation(bEvent.getButton()))
                        .append("\n");
            case StringSelectInteractionEvent sEvent ->
                builder.append("selected ")
                        .append(SelectionMenuProcessor.getSelectionMenuDebugText(sEvent))
                        .append("\n");
            case ModalInteractionEvent mEvent ->
                builder.append("used modal ")
                        .append(ModalListener.getModalDebugText(mEvent))
                        .append("\n");
            default ->
                builder.append("initiated an unexpected event of type `")
                        .append(event.getType())
                        .append("`\n");
        }

        return builder.toString();
    }

    /**
     * Get the most relevant log channel for this source. Priority is to severity.channelName, then "#bot-log", then returns null.
     *
     * @param severity - The severity of the log message, used to find the appropriate channel based on LogSeverity.channelName
     * @return The most relevant logging TextChannel
     */
    @Nullable
    TextChannel getLogChannel(@Nonnull LogSeverity severity) {
        Guild guild = AsyncTI4DiscordBot.guildPrimary;
        if (guild == null && guildId != null) {
            guild = AsyncTI4DiscordBot.jda.getGuildById(guildId);
        }
        if (guild == null) return null;

        return guild.getTextChannelsByName(severity.channelName, false).stream()
                .findFirst()
                .orElse(guild.getTextChannelsByName("bot-log", false).stream()
                        .findFirst()
                        .orElse(null));
    }

    public Guild getGuild() {
        return guildId == null ? null : AsyncTI4DiscordBot.jda.getGuildById(guildId);
    }

    @Nonnull
    String getOriginTimeFormatted() {
        return String.format("**__%s__** ", originTime);
    }
}
