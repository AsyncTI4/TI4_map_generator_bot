package ti4.commands.uncategorized;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.generator.MapRenderPipeline;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class ShowGame extends ti4.commands2.GameStateCommand {

    public ShowGame() {
        super(false, false);
    }

    @Override
    public String getName() {
        return Constants.SHOW_GAME;
    }

    @Override
    public String getDescription() {
        return "Show selected map";
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        DisplayType displayType = null;
        OptionMapping statsOption = event.getOption(Constants.DISPLAY_TYPE);
        if (statsOption != null) {
            String temp = statsOption.getAsString();
            if (temp.equals(DisplayType.split.getValue())) {
                displayType = DisplayType.map;
                MapRenderPipeline.render(game, event, displayType,
                                fileUpload -> MessageHelper.sendFileUploadToChannel(event.getChannel(), fileUpload));
                displayType = DisplayType.stats;
            } else {
                for (DisplayType i : DisplayType.values()) {
                    if (temp.equals(i.getValue())) {
                        displayType = i;
                        break;
                    }
                }
            }
        }
        if (displayType == null) {
            displayType = DisplayType.all;
        }
        simpleShowGame(game, event, displayType);
    }

    @ButtonHandler("showGameAgain")
    public static void simpleShowGame(Game game, GenericInteractionCreateEvent event) {
        simpleShowGame(game, event, DisplayType.all);
    }

    @ButtonHandler("showMap")
    public static void showMap(Game game, ButtonInteractionEvent event) {
        MapRenderPipeline.render(game, event, DisplayType.map, fileUpload -> MessageHelper.sendEphemeralFileInResponseToButtonPress(fileUpload, event));
    }

    @ButtonHandler("showPlayerAreas")
    public static void showPlayArea(Game game, ButtonInteractionEvent event) {
        MapRenderPipeline.render(game, event, DisplayType.stats, fileUpload -> MessageHelper.sendEphemeralFileInResponseToButtonPress(fileUpload, event));
    }

    public static boolean includeButtons(DisplayType displayType) {
        return switch (displayType) {
            case wormholes, anomalies, legendaries, empties, aetherstream, spacecannon, traits, techskips, attachments,
                 shipless -> false;
            default -> true;
        };
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
}
