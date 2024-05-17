package ti4.commands.uncategorized;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.generator.MapGenerator;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.message.MessageHelper;

public class ShowGame implements Command {

    @Override
    public String getActionID() {
        return Constants.SHOW_GAME;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        if (!event.getName().equals(getActionID())) {
            return false;
        }
        OptionMapping option = event.getOption(Constants.GAME_NAME);
        if (option != null) {
            String mapName = option.getAsString();
            if (!GameManager.getInstance().getGameNameToGame().containsKey(mapName)) {
                MessageHelper.replyToMessage(event, "Game with such name does not exists, use /list_games");
                return false;
            }
        } else {
            Game userActiveGame = GameManager.getInstance().getUserActiveGame(event.getUser().getId());
            if (userActiveGame == null) {
                MessageHelper.replyToMessage(event, "No active game set, need to specify what map to show");
                return false;
            }
        }
        return true;
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
            if (temp.equals(DisplayType.all.getValue())) {
                displayType = DisplayType.all;
            } else if (temp.equals(DisplayType.map.getValue())) {
                displayType = DisplayType.map;
            } else if (temp.equals(DisplayType.stats.getValue())) {
                displayType = DisplayType.stats;
            } else if (temp.equals(DisplayType.split.getValue())) {
                displayType = DisplayType.map;
                MapGenerator.saveImage(game, displayType, event)
                    .thenAccept(fileUpload -> MessageHelper.sendFileUploadToChannel(event.getChannel(), fileUpload));
                displayType = DisplayType.stats;
            } else if (temp.equals(DisplayType.system.getValue())) {
                displayType = DisplayType.system;
            }
        }
        simpleShowGame(game, event, displayType);
    }

    public static void simpleShowGame(Game game, GenericInteractionCreateEvent event) {
        simpleShowGame(game, event, DisplayType.all);
    }

    public static void simpleShowGame(Game game, GenericInteractionCreateEvent event, DisplayType displayType) {
        MapGenerator.saveImage(game, displayType, event).thenAccept(fileUpload -> {
            List<Button> buttons = new ArrayList<>();
            if (!game.isFoWMode()) {
                Button linkToWebsite = Button.link("https://ti4.westaddisonheavyindustries.com/game/" + game.getName(), "Website View");
                buttons.add(linkToWebsite);
                buttons.add(Button.success("gameInfoButtons", "Player Info"));
            }
            buttons.add(Button.success("cardsInfo", "Cards Info"));
            buttons.add(Button.primary("offerDeckButtons", "Show Decks"));
            buttons.add(Button.secondary("showGameAgain", "Show Game"));

            // Divert map image to the botMapUpdatesThread event channel is actions channel is the same
            MessageChannel channel = event.getMessageChannel();
            if (!game.isFoWMode() && game.getActionsChannel() != null && game.getBotMapUpdatesThread() != null && channel.equals(game.getActionsChannel())) {
                channel = game.getBotMapUpdatesThread();
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Map Image sent to " + game.getBotMapUpdatesThread().getJumpUrl());
            }

            MessageHelper.sendFileToChannelWithButtonsAfter(channel, fileUpload, null, buttons);
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
