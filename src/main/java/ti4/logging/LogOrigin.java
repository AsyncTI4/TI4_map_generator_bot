package ti4.logging;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Getter;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import org.jetbrains.annotations.NotNull;
import ti4.discord.interactions.listeners.context.ListenerContext;
import ti4.discord.interactions.selections.SelectionMenuProcessor;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.DateTimeHelper;

@Getter
public class LogOrigin {

    @Nullable
    private final String eventString;

    @Nullable
    private final String gameInfo;

    private final String originTime = DateTimeHelper.getCurrentTimestamp();

    public LogOrigin() {
        eventString = null;
        gameInfo = null;
    }

    public LogOrigin(@Nonnull GenericInteractionCreateEvent event) {
        eventString = buildEventString(event);
        gameInfo = null;
    }

    public LogOrigin(@Nullable Game game) {
        gameInfo = buildGameInfo(game);
        eventString = null;
    }

    public LogOrigin(@Nullable Player player) {
        if (player == null) {
            BotLogger.warning("LocationSource created from null player. This will not attribute messages.");
            gameInfo = null;
        } else {
            Game game = player.getGame();
            gameInfo = buildGameInfo(game);
        }
        eventString = null;
    }

    public LogOrigin(@Nonnull GenericInteractionCreateEvent event, @Nullable ListenerContext context) {
        eventString = buildEventString(event);
        gameInfo = context != null ? buildGameInfo(context.getGame()) : null;
    }

    public LogOrigin(@Nonnull GenericInteractionCreateEvent event, @Nullable Game game) {
        eventString = buildEventString(event);
        gameInfo = buildGameInfo(game);
    }

    public LogOrigin(@Nonnull GenericInteractionCreateEvent event, @Nullable Player player) {
        eventString = buildEventString(event);
        gameInfo = player != null ? buildGameInfo(player.getGame()) : null;
    }

    private static String buildGameInfo(@Nullable Game game) {
        if (game == null) return null;
        return "\nGame info: " + game.gameJumpLinks();
    }

    @NotNull
    private static String buildEventString(@Nonnull GenericInteractionCreateEvent event) {
        StringBuilder builder =
                new StringBuilder().append(event.getUser().getEffectiveName()).append(' ');

        switch (event) {
            case SlashCommandInteractionEvent sEvent ->
                builder.append("used command `")
                        .append(sEvent.getCommandString())
                        .append("`\n");
            case ButtonInteractionEvent bEvent ->
                builder.append("pressed button ")
                        .append(ButtonHelper.getButtonRepresentation(bEvent.getButton()))
                        .append(buildInteractionLocationText(bEvent.getChannel().getName(), bEvent.getMessage()))
                        .append('\n');
            case StringSelectInteractionEvent sEvent ->
                builder.append("selected ")
                        .append(SelectionMenuProcessor.getSelectionMenuDebugText(sEvent))
                        .append(buildInteractionLocationText(sEvent.getChannel().getName(), sEvent.getMessage()))
                        .append('\n');
            case ModalInteractionEvent mEvent ->
                builder.append("used modal ")
                        .append(buildModalDebugText(mEvent))
                        .append('\n');
            default ->
                builder.append("initiated an unexpected event of type `")
                        .append(event.getType())
                        .append("`\n");
        }

        return builder.toString();
    }

    @Nonnull
    private static String buildInteractionLocationText(@Nullable String channelName, @Nullable Message message) {
        if (message == null) {
            return channelName == null ? "" : " in: `" + channelName + "`";
        }
        String messageJumpUrl = message.getJumpUrl();
        if (channelName == null) {
            return "";
        }
        return " in: [" + channelName + "](" + messageJumpUrl + ")";
    }

    @Nonnull
    private static String buildModalDebugText(ModalInteractionEvent event) {
        return "INPUT:\n```\n" + "MenuID: " + event.getModalId() + "\n```";
    }

    @Nonnull
    String getOriginTimeFormatted() {
        return String.format("**__%s__** ", originTime);
    }
}
