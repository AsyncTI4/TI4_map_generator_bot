package ti4.commands.tokens;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.generator.GenerateMap;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.*;
import ti4.message.MessageHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

abstract public class AddRemoveToken implements Command {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String userID = event.getUser().getId();
        GameManager gameManager = GameManager.getInstance();
        if (!gameManager.isUserWithActiveGame(userID)) {
            MessageHelper.replyToMessage(event, "Set your active game using: /set_game gameName");
        } else {
            OptionMapping option = event.getOption(Constants.FACTION_COLOR);
            ArrayList<String> colors = new ArrayList<>();
            Game activeGame = gameManager.getUserActiveGame(userID);
            if (option != null) {
                String colorString = option.getAsString().toLowerCase();
                colorString = colorString.replace(" ", "");
                StringTokenizer colorTokenizer = new StringTokenizer(colorString, ",");
                while (colorTokenizer.hasMoreTokens()) {
                    String color = Helper.getColorFromString(activeGame, colorTokenizer.nextToken());
                    if (!colors.contains(color)) {
                        colors.add(color);
                        if (!Mapper.isColorValid(color)) {
                            MessageHelper.replyToMessage(event, "Color/faction not valid: " + color);
                            return;
                        }
                    }
                }
            } else {
                Player player = activeGame.getPlayer(userID);
                player = Helper.getGamePlayer(activeGame, player, event, null);
                if (player == null) {
                    MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
                    return;
                }
                colors.add(player.getColor());

            }
            OptionMapping tileOption = event.getOption(Constants.TILE_NAME);
            if (tileOption != null) {
                String tileID = AliasHandler.resolveTile(tileOption.getAsString().toLowerCase());

                if (activeGame.isTileDuplicated(tileID)) {
                    MessageHelper.replyToMessage(event, "Duplicate tile name found, please use position coordinates");
                    return;
                }
                Tile tile = activeGame.getTile(tileID);
                if (tile == null) {
                    tile = activeGame.getTileByPosition(tileID);
                }
                if (tile == null) {
                    MessageHelper.replyToMessage(event, "Tile in map not found");
                    return;
                }

                parsingForTile(event, colors, tile, activeGame);
                GameSaveLoadManager.saveMap(activeGame, event);

                File file = GenerateMap.getInstance().saveImage(activeGame, event);
                //MessageHelper.replyToMessage(event, file);
                
                    List<Button> buttonsWeb = new ArrayList<>();
                    if(!activeGame.isFoWMode()){
                        Button linkToWebsite = Button.link("https://ti4.westaddisonheavyindustries.com/game/"+ activeGame.getName(),"Website View");
                        buttonsWeb.add(linkToWebsite);
                    }
                    buttonsWeb.add(Button.success("cardsInfo","Cards Info"));
                    buttonsWeb.add(Button.secondary("showGameAgain","Show Game"));
                    MessageHelper.sendFileToChannelWithButtonsAfter(event.getChannel(), file, "",buttonsWeb);
                
            } else {
                MessageHelper.replyToMessage(event, "Tile needs to be specified.");
            }
        }
    }

    abstract void parsingForTile(SlashCommandInteractionEvent event, ArrayList<String> color, Tile tile, Game activeGame);
    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return event.getName().equals(getActionID());
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
                Commands.slash(getActionID(), getActionDescription())
                        .addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name").setRequired(true).setAutoComplete(true))
                        .addOptions(new OptionData(OptionType.STRING, Constants.PLANET, "Planet name").setAutoComplete(true))
                        .addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color")
                                .setAutoComplete(true))
        );
    }

    abstract protected String getActionDescription();


}
