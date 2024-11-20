package ti4.commands2.map;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Tile;
import ti4.service.map.AddTileService;

class AddTile extends AddRemoveTile {

    public AddTile() {
        super(Constants.ADD_TILE, "Add tile to map");
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "Tile name", true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.POSITION, "Tile position on map", true));
    }

    @Override
    protected void tileAction(Tile tile, String position) {
        AddTileService.addTile(getGame(), tile);
    }
}
