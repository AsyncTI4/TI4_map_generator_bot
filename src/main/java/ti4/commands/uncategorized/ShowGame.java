package ti4.commands.uncategorized;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.buttons.Buttons;
import ti4.commands.Command;
import ti4.generator.MapRenderPipeline;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.helpers.SlashCommandAcceptanceHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.message.MessageHelper;

import java.util.ArrayList;
import java.util.List;

public class ShowGame implements Command {

    @Override
    public String getActionID() {
        return Constants.SHOW_GAME;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return SlashCommandAcceptanceHelper.shouldAcceptIfActivePlayerOfGame(getActionID(), event);

    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game;
        OptionMapping option = event.getOption(Constants.GAME_NAME);
        GameManager gameManager = GameManager.getInstance();
        if (option != null) {
            String mapName = option.getAsString().toLowerCase();
            game = gameManager.getGame(mapName);
        } else {
            game = gameManager.getUserActiveGame(event.getUser().getId());
        }
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
                MessageChannel channel = event.getMessageChannel();
                if (!game.isFowMode() && game.getActionsChannel() != null && game.getBotMapUpdatesThread() != null && channel.equals(game.getActionsChannel())) {
                    channel = game.getBotMapUpdatesThread();
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Map Image sent to " + game.getBotMapUpdatesThread().getJumpUrl());
                }

                MessageHelper.sendFileToChannelWithButtonsAfter(channel, fileUpload, null, buttons);
            } else {
                MessageChannel channel = event.getMessageChannel();
                if (!game.isFowMode() && game.getActionsChannel() != null && game.getBotMapUpdatesThread() != null && channel.equals(game.getActionsChannel())) {
                    channel = game.getBotMapUpdatesThread();
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Map Image sent to " + game.getBotMapUpdatesThread().getJumpUrl());
                }

                MessageHelper.sendFileUploadToChannel(channel, fileUpload);
            }
        });
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
            Commands.slash(getActionID(), "Shows selected map")
                .addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "Map name to be shown").setAutoComplete(true))
                .addOptions(new OptionData(OptionType.STRING, Constants.DISPLAY_TYPE, "Show map in specific format. all, map, stats").setAutoComplete(true)));
    }
}
