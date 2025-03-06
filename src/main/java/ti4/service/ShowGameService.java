package ti4.service;

import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.DisplayType;
import ti4.image.MapRenderPipeline;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.fow.UserOverridenSlashCommandInteractionEvent;

@UtilityClass
public class ShowGameService {

    public static void simpleEphemeralShowGame(Game game, GenericInteractionCreateEvent event) {
        ephemeralShowGame(game, event, DisplayType.map);
    }

    public static void simpleShowGame(Game game, GenericInteractionCreateEvent event) {
        simpleShowGame(game, event, DisplayType.all);
    }

    public static void simpleShowGame(Game game, GenericInteractionCreateEvent event, DisplayType displayType) {
        MapRenderPipeline.queue(game, event, displayType, fileUpload -> {
            if (includeButtons(displayType)) {
                List<Button> buttons = Buttons.mapImageButtons(game);

                // Divert map image to the botMapUpdatesThread event channel is actions channel is the same
                MessageChannel channel = sendMessage(game, event);
                ButtonHelper.sendFileWithCorrectButtons(channel, fileUpload, null, buttons, game);
            } else {
                MessageChannel channel = sendMessage(game, event);
                MessageHelper.sendFileUploadToChannel(channel, fileUpload);
            }
            if (event instanceof ButtonInteractionEvent buttonEvent) {
                buttonEvent.getHook().deleteOriginal().queue();
            }
        });
    }

    public static void ephemeralShowGame(Game game, GenericInteractionCreateEvent event, DisplayType displayType) {
        MapRenderPipeline.queue(game, event, displayType, fileUpload -> {
            MessageHelper.sendEphemeralFileInResponseToButtonPress(fileUpload, event);
        });
    }

    private static MessageChannel sendMessage(Game game, GenericInteractionCreateEvent event) {
        MessageChannel channel = event.getMessageChannel();
        if (!game.isFowMode() && game.getActionsChannel() != null && game.getBotMapUpdatesThread() != null && channel.equals(game.getActionsChannel())) {
            channel = game.getBotMapUpdatesThread();
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Map Image sent to " + game.getBotMapUpdatesThread().getJumpUrl());
        } else if (game.isFowMode()) {
            Player player = game.getPlayer(event.getUser().getId());
            MessageChannel privateChannel = player != null ? player.getPrivateChannel() : null;
            if (!event.getClass().equals(UserOverridenSlashCommandInteractionEvent.class)
                && game.getRealPlayers().contains(player) && !game.getPlayersWithGMRole().contains(player)
                && privateChannel != null && !channel.equals(privateChannel)) {
                channel = privateChannel;
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Map Image sent to " + ((TextChannel) privateChannel).getJumpUrl());
            }
        }
        return channel;
    }

    public static boolean includeButtons(DisplayType displayType) {
        return switch (displayType) {
            case wormholes, anomalies, legendaries, empties, aetherstream, spacecannon, traits, techskips, attachments, shipless -> false;
            default -> true;
        };
    }
}
