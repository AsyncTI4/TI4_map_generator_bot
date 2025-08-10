package ti4.commands.map;

import java.util.Set;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.ResourceHelper;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.service.map.AddTileService;

class AddTile extends GameStateSubcommand {

    public AddTile() {
        super(Constants.ADD_TILE, "Add tile to map", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "Tile name", true).setAutoComplete(true));
        addOptions(new OptionData(
                OptionType.STRING, Constants.POSITION, "Tile position on map. Accepts comma separated list", true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String planetTileName =
                AliasHandler.resolveTile(event.getOptions().get(0).getAsString().toLowerCase());
        String positionOption = event.getOptions().get(1).getAsString();
        Set<String> positions = Helper.getSetFromCSV(positionOption);

        for (String position : positions) {
            if (!PositionMapper.isTilePositionValid(position)) {
                MessageHelper.replyToMessage(event, "Position `" + position + "` not allowed");
                return;
            }
        }

        String tileName = Mapper.getTileID(planetTileName);
        if (tileName == null) {
            MessageHelper.replyToMessage(event, "Could not find tile: " + planetTileName);
            return;
        }
        String tilePath = ResourceHelper.getInstance().getTileFile(tileName);
        if (tilePath == null) {
            MessageHelper.replyToMessage(event, "Could not find tile: " + planetTileName);
            return;
        }

        Game game = getGame();
        Boolean isFowPrivate = null;
        if (game.isFowMode()) {
            isFowPrivate = event.getChannel().getName().endsWith(Constants.PRIVATE_CHANNEL);
        }

        if (isFowPrivate != null && isFowPrivate && !game.isAgeOfExplorationMode()) {
            MessageHelper.replyToMessage(event, "Cannot run this command in a private channel.");
            return;
        }

        for (String position : positions) {
            Tile tile = new Tile(planetTileName, position);
            if (tile.isMecatol() && !game.isLiberationC4Mode()) {
                AddTileService.addCustodianToken(tile, game);
            }

            AddTileService.addTile(getGame(), tile);
        }

        game.rebuildTilePositionAutoCompleteList();
    }
}
