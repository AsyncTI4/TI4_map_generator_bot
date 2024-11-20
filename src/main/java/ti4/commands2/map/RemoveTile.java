package ti4.commands2.map;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Tile;

class RemoveTile extends AddRemoveTile {

    public RemoveTile() {
        super(Constants.REMOVE_TILE, "Remove tile from map");
        addOptions(new OptionData(OptionType.STRING, Constants.POSITION, "Tile position on map. Accepts comma separated list", true).setAutoComplete(true));
    }

    @Override
    protected void tileAction(Tile tile, String position) {
        getGame().removeTile(position);
    }
}
