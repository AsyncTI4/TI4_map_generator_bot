package ti4.commands.tokens;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.commands2.CommandHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.service.ShowGameService;

abstract public class AddRemoveToken implements Command {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String userID = event.getUser().getId();
        if (!GameManager.isUserWithActiveGame(userID)) {
            MessageHelper.replyToMessage(event, "Set your active game using: /set_game gameName");
            return;
        }

        String tileOptions = event.getOption(Constants.TILE_NAME, null, OptionMapping::getAsString);
        if (tileOptions == null) {
            MessageHelper.replyToMessage(event, "Tile needs to be specified.");
            return;
        }

        OptionMapping factionOrColour = event.getOption(Constants.FACTION_COLOR);
        List<String> colors = new ArrayList<>();
        Game game = GameManager.getUserActiveGame(userID);
        if (factionOrColour != null) {
            String colorString = factionOrColour.getAsString().toLowerCase();
            colorString = colorString.replace(" ", "");
            StringTokenizer colorTokenizer = new StringTokenizer(colorString, ",");
            while (colorTokenizer.hasMoreTokens()) {
                String color = CommandHelper.getColorFromString(game, colorTokenizer.nextToken());
                if (!colors.contains(color)) {
                    colors.add(color);
                    if (!Mapper.isValidColor(color)) {
                        MessageHelper.replyToMessage(event, "Color/faction not valid: " + color);
                        return;
                    }
                }
            }
        } else {
            Player player = CommandHelper.getPlayerFromEvent(game, event);
            if (player != null) {
                colors.add(player.getColor());
            }
        }

        List<Tile> tiles = new ArrayList<>();
        String tileString = tileOptions.toLowerCase().replace(" ", "");
        StringTokenizer tileTokenizer = new StringTokenizer(tileString, ",");
        while (tileTokenizer.hasMoreTokens()) {
            String tileID = AliasHandler.resolveTile(tileTokenizer.nextToken());

            if (game.isTileDuplicated(tileID)) {
                MessageHelper.replyToMessage(event, "Duplicate tile name found, please use position coordinates");
                return;
            }
            Tile tile = game.getTile(tileID);
            if (tile == null) {
                tile = game.getTileByPosition(tileID);
            }
            if (tile == null) {
                MessageHelper.replyToMessage(event, "Tile in map not found");
                return;
            }
            tiles.add(tile);
        }

        for (Tile tile : tiles) {
            parsingForTile(event, colors, tile, game);
        }
        GameSaveLoadManager.saveGame(game, event);
        ShowGameService.simpleShowGame(game, event);
    }

    abstract void parsingForTile(SlashCommandInteractionEvent event, List<String> color, Tile tile, Game game);

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void register(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(Commands.slash(getName(), getActionDescription())
                .addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name").setRequired(true).setAutoComplete(true))
                .addOptions(new OptionData(OptionType.STRING, Constants.PLANET, "Planet name").setAutoComplete(true))
                .addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Source faction or color (default is you)").setAutoComplete(true)));
    }

    abstract protected String getActionDescription();
}
