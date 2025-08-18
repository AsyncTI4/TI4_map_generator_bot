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

    @Nullable
    private Guild guild;

    @Nullable
    private GuildChannel channel;

    @Nullable
    private GenericInteractionCreateEvent event;

    @Nullable
    private Game game;

    @Nullable
    private Player player;

    private final String originTime;

    public LogOrigin(@Nonnull Guild guild) {
        this.guild = guild;
        originTime = DateTimeHelper.getCurrentTimestamp();
    }

    public LogOrigin(@Nonnull GuildChannel channel) {
        this.channel = channel;
        guild = channel.getGuild();
        originTime = DateTimeHelper.getCurrentTimestamp();
    }

    public LogOrigin(@Nonnull GenericInteractionCreateEvent event) {
        this.event = event;
        if (event.isFromGuild()) {
            channel = event.getGuildChannel();
            guild = event.getGuild();
        } else {
            BotLogger.warning("LocationSource created from non-guild event. This will not attribute messages.");
        }
        originTime = DateTimeHelper.getCurrentTimestamp();
    }

    public LogOrigin(@Nonnull Game game) {
        this.game = game;
        guild = game.getGuild();
        channel = game.getMainGameChannel();
        originTime = DateTimeHelper.getCurrentTimestamp();
    }

    public LogOrigin(@Nullable Player player) {
        if (player != null) {
            this.player = player;
            game = player.getGame();
        }
        if (game != null) {
            guild = game.getGuild();
            channel = game.getMainGameChannel();
        } else {
            BotLogger.warning("LocationSource created from player with null game. This will not attribute messages.");
        }
        originTime = DateTimeHelper.getCurrentTimestamp();
    }

    public LogOrigin(@Nullable GenericInteractionCreateEvent event, @Nonnull Game game) {
        this.game = game;
        guild = game.getGuild();
        this.event = event;
        if (event != null && event.isFromGuild()) channel = event.getGuildChannel();
        else channel = game.getMainGameChannel();
        originTime = DateTimeHelper.getCurrentTimestamp();
    }

    public LogOrigin(@Nullable GenericInteractionCreateEvent event, @Nonnull Player player) {
        this.player = player;
        game = player.getGame();
        if (game != null) guild = game.getGuild();
        this.event = event;
        if (event != null && event.isFromGuild()) {
            channel = event.getGuildChannel();
            guild = event.getGuild();
        } else if (game != null) {
            channel = game.getMainGameChannel();
        }
        originTime = DateTimeHelper.getCurrentTimestamp();
    }

    @Nullable
    StringBuilder getGameInfo() {
        if (game != null) {
            StringBuilder builder = new StringBuilder().append("\nGame info: ");
            builder.append(game.gameJumpLinks());
            return builder;
        }
        return null;
    }

    @Nullable
    StringBuilder getEventString() {
        if (event == null) return null;

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

        return builder;
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
        if (guild == null) guild = this.guild;
        if (guild == null) return null;

        return guild.getTextChannelsByName(severity.channelName, false).stream()
                .findFirst()
                .orElse(guild.getTextChannelsByName("bot-log", false).stream()
                        .findFirst()
                        .orElse(null));
    }

    @Nonnull
    String getOriginTimeFormatted() {
        return String.format("**__%s__** ", originTime);
    }
}
