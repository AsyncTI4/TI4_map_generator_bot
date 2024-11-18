package ti4.service;

import java.util.ArrayList;
import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.DisplayType;
import ti4.image.MapRenderPipeline;
import ti4.map.Game;
import ti4.message.MessageHelper;

@UtilityClass
public class ShowGameService {

    public static void simpleShowGame(Game game, GenericInteractionCreateEvent event) {
        simpleShowGame(game, event, DisplayType.all);
    }

    public static void simpleShowGame(Game game, GenericInteractionCreateEvent event, DisplayType displayType) {
        MapRenderPipeline.render(game, event, displayType, fileUpload -> {
            if (includeButtons(displayType)) {
                List<Button> buttons = new ArrayList<>();
                if (!game.isFowMode()) {
                    Button linkToWebsite = Button.link("https://ti4.westaddisonheavyindustries.com/game/" + game.getName(), "Website View");
                    buttons.add(linkToWebsite);
                    buttons.add(Buttons.green("gameInfoButtons", "Player Info"));
                }
                buttons.add(Buttons.green("cardsInfo", "Cards Info"));
                buttons.add(Buttons.blue("offerDeckButtons", "Show Decks"));
                buttons.add(Buttons.gray("showGameAgain", "Show Game"));

                // Divert map image to the botMapUpdatesThread event channel is actions channel is the same
                MessageChannel channel =sendMessage(game, event);
                MessageHelper.sendFileToChannelWithButtonsAfter(channel, fileUpload, null, buttons);
            } else {
                MessageChannel channel = sendMessage(game, event);
                MessageHelper.sendFileUploadToChannel(channel, fileUpload);
            }
        });
    }

    private static MessageChannel sendMessage(Game game, GenericInteractionCreateEvent event) {
        MessageChannel channel = event.getMessageChannel();
        if (!game.isFowMode() && game.getActionsChannel() != null && game.getBotMapUpdatesThread() != null && channel.equals(game.getActionsChannel())) {
            channel = game.getBotMapUpdatesThread();
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Map Image sent to " + game.getBotMapUpdatesThread().getJumpUrl());
        }
        return channel;
    }

    public static boolean includeButtons(DisplayType displayType) {
        return switch (displayType) {
            case wormholes, anomalies, legendaries, empties, aetherstream, spacecannon, traits, techskips, attachments,
                 shipless -> false;
            default -> true;
        };
    }
}
