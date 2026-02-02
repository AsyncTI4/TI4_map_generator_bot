package ti4.service;

import java.util.List;
import java.util.function.Consumer;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.DisplayType;
import ti4.image.MapRenderPipeline;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.service.fow.UserOverridenGenericInteractionCreateEvent;
import ti4.spring.api.image.GameImageService;
import ti4.spring.context.SpringContext;

@UtilityClass
public class ShowGameService {

    public static void simpleEphemeralShowGame(Game game, GenericInteractionCreateEvent event) {
        ephemeralShowGame(game, event, DisplayType.map);
    }

    public static void simpleShowGame(Game game, GenericInteractionCreateEvent event) {
        simpleShowGame(game, event, DisplayType.all);
    }

    public static void simpleShowGame(Game game, GenericInteractionCreateEvent event, DisplayType displayType) {
        boolean shouldPersistFullMapMessageId = displayType == DisplayType.all && !game.isFowMode();
        boolean shouldPersistFowMapMessageId = displayType == DisplayType.all && game.isFowMode();

        // For non-FoW games: persist the full map message ID
        Consumer<Message> persistMessageId = shouldPersistFullMapMessageId
                ? msg -> SpringContext.getBean(GameImageService.class)
                        .saveDiscordMessageId(
                                game,
                                msg.getIdLong(),
                                msg.getGuild().getIdLong(),
                                msg.getChannel().getIdLong())
                : null;

        // For FoW games: persist the player-specific map message ID
        String playerId = event.getUser().getId();
        Consumer<Message> persistFowMessageId = shouldPersistFowMapMessageId
                ? msg -> SpringContext.getBean(GameImageService.class)
                        .savePlayerDiscordMessageId(
                                game.getName(),
                                playerId,
                                msg.getIdLong(),
                                msg.getChannel().getIdLong())
                : null;

        MapRenderPipeline.queue(game, event, displayType, fileUpload -> {
            if (includeButtons(displayType)) {
                List<Button> buttons = Buttons.mapImageButtons(game);

                // Divert map image to the botMapUpdatesThread event channel is actions channel is the same
                MessageChannel channel = sendMessage(game, event);
                // Use FoW callback when sending to player's private channel, otherwise use full map callback
                Consumer<Message> callback = (game.isFowMode() && isSendingToPrivateChannel(game, event))
                        ? persistFowMessageId
                        : persistMessageId;
                ButtonHelper.sendFileWithCorrectButtons(channel, fileUpload, null, buttons, game, callback);
            } else {
                MessageChannel channel = sendMessage(game, event);
                Consumer<Message> callback = (game.isFowMode() && isSendingToPrivateChannel(game, event))
                        ? persistFowMessageId
                        : persistMessageId;
                MessageHelper.sendFileUploadToChannel(channel, fileUpload, callback);
            }
            if (event instanceof ButtonInteractionEvent buttonEvent) {
                buttonEvent.getHook().deleteOriginal().queue(Consumers.nop(), BotLogger::catchRestError);
            }
        });
    }

    /**
     * Check if the map is being sent to a player's private channel (for FoW games).
     */
    private static boolean isSendingToPrivateChannel(Game game, GenericInteractionCreateEvent event) {
        if (!game.isFowMode()) {
            return false;
        }
        Player player = game.getPlayer(event.getUser().getId());
        MessageChannel privateChannel = player != null ? player.getPrivateChannel() : null;
        return !event.getClass().equals(UserOverridenGenericInteractionCreateEvent.class)
                && game.getRealPlayers().contains(player)
                && !game.getPlayersWithGMRole().contains(player)
                && privateChannel != null
                && !event.getMessageChannel().equals(privateChannel);
    }

    private static void ephemeralShowGame(Game game, GenericInteractionCreateEvent event, DisplayType displayType) {
        MapRenderPipeline.queue(
                game,
                event,
                displayType,
                fileUpload -> MessageHelper.sendEphemeralFileInResponseToButtonPress(fileUpload, event));
    }

    private static MessageChannel sendMessage(Game game, GenericInteractionCreateEvent event) {
        MessageChannel channel = event.getMessageChannel();
        if (!game.isFowMode()
                && game.getActionsChannel() != null
                && game.getBotMapUpdatesThread() != null
                && channel.equals(game.getActionsChannel())) {
            channel = game.getBotMapUpdatesThread();
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "Map Image sent to " + game.getBotMapUpdatesThread().getJumpUrl());
        } else if (game.isFowMode()) {
            Player player = game.getPlayer(event.getUser().getId());
            MessageChannel privateChannel = player != null ? player.getPrivateChannel() : null;
            if (!event.getClass().equals(UserOverridenGenericInteractionCreateEvent.class)
                    && game.getRealPlayers().contains(player)
                    && !game.getPlayersWithGMRole().contains(player)
                    && privateChannel != null
                    && !channel.equals(privateChannel)) {
                channel = privateChannel;
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(), "Map Image sent to " + ((TextChannel) privateChannel).getJumpUrl());
            }
        }
        return channel;
    }

    private static boolean includeButtons(DisplayType displayType) {
        return switch (displayType) {
            case wormholes,
                    anomalies,
                    legendaries,
                    empties,
                    aetherstream,
                    spacecannon,
                    traits,
                    techskips,
                    attachments,
                    shipless,
                    unlocked -> false;
            default -> true;
        };
    }
}
