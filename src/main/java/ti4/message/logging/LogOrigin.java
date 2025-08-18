package ti4.message.logging;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Getter;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import org.jetbrains.annotations.NotNull;
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
    private final String guildId;

    @Nullable
    private final String eventString;

    @Nullable
    private final String gameInfo;

    private final String originTime = DateTimeHelper.getCurrentTimestamp();

    public LogOrigin(@Nonnull Guild guild) {
        guildId = guild.getId();
        eventString = null;
        gameInfo = null;
    }

    public LogOrigin(@Nonnull GuildChannel channel) {
        guildId = channel.getGuild().getId();
        eventString = null;
        gameInfo = null;
    }

    public LogOrigin(@Nonnull GenericInteractionCreateEvent event) {
        guildId = event.getGuild().getId();
        eventString = buildEventString(event);
        gameInfo = null;
    }

    public LogOrigin(@Nonnull Game game) {
        guildId = getGuildId(game);
        gameInfo = buildGameInfo(game);
        eventString = null;
    }

    public LogOrigin(@Nullable Player player) {
        if (player == null) {
            BotLogger.warning("LocationSource created from null player. This will not attribute messages.");
            guildId = null;
            gameInfo = null;
        } else {
            Game game = player.getGame();
            guildId = getGuildId(game);
            gameInfo = buildGameInfo(game);
        }
        eventString = null;
    }

    public LogOrigin(@Nonnull GenericInteractionCreateEvent event, @Nonnull Game game) {
        guildId = event.getGuild().getId();
        eventString = buildEventString(event);
        gameInfo = buildGameInfo(game);
    }

    public LogOrigin(@Nonnull GenericInteractionCreateEvent event, @Nullable Player player) {
        guildId = event.getGuild().getId();
        eventString = buildEventString(event);
        gameInfo = player != null ? buildGameInfo(player.getGame()) : null;
    }

    @Nullable
    private static String getGuildId(Game game) {
        return game.getGuild() != null ? game.getGuild().getId() : null;
    }

    @NotNull
    private static String buildGameInfo(@Nonnull Game game) {
        return "\nGame info: " + game.gameJumpLinks();
    }

    @NotNull
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

    Guild getGuild() {
        return guildId == null ? null : AsyncTI4DiscordBot.jda.getGuildById(guildId);
    }

    @Nonnull
    String getOriginTimeFormatted() {
        return String.format("**__%s__** ", originTime);
    }
}
