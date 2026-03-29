package ti4.message.logging;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Getter;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import org.jetbrains.annotations.NotNull;
import ti4.helpers.ButtonHelper;
import ti4.helpers.DateTimeHelper;
import ti4.listeners.ModalListener;
import ti4.listeners.context.ListenerContext;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.persistence.GameManager;
import ti4.selections.SelectionMenuProcessor;
import ti4.service.game.GameNameService;

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

    @Nullable
    private static String getGameChannelJumpLink(@Nonnull GenericInteractionCreateEvent event) {
        if (!event.isFromGuild()) return null;
        String gameName = GameNameService.getGameNameFromChannel(event);
        if (!GameManager.isValid(gameName)) return null;
        return event.getMessageChannel().getJumpUrl();
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

        String gameChannelJumpLink = getGameChannelJumpLink(event);
        if (gameChannelJumpLink != null) {
            builder.append("Channel: ").append(gameChannelJumpLink).append("\n");
        }

        return builder.toString();
    }

    @Nonnull
    String getOriginTimeFormatted() {
        return String.format("**__%s__** ", originTime);
    }
}
