package ti4.commands.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.units.AddRemoveUnits;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.*;
import ti4.message.MessageHelper;

public class NovaSeed extends SpecialSubcommandData {
    public NovaSeed() {
        super(Constants.NOVA_SEED, "Nova seed a system");
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player using nova seed").setRequired(false));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color using nova seed").setAutoComplete(true));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.DESTROY_OTHER_UNITS, "Destroy other players units"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveMap();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        player = Helper.getPlayer(activeGame, player, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }

        OptionMapping tileOption = event.getOption(Constants.TILE_NAME);
        if (tileOption == null){
            MessageHelper.sendMessageToChannel(event.getChannel(), "Specify a tile");
            return;
        }
        String tileID = AliasHandler.resolveTile(tileOption.getAsString().toLowerCase());
        Tile tile = AddRemoveUnits.getTile(event, tileID, activeGame);
        if (tile == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not resolve tileID:  `" + tileID + "`. Tile not found");
            return;
        }

        //Remove all other players units from the tile in question
        OptionMapping destroyOption = event.getOption(Constants.DESTROY_OTHER_UNITS);
        if (destroyOption == null || destroyOption.getAsBoolean()) {
            for (Player player_ : activeGame.getPlayers().values()) {
                if (player_ != player) {
                    tile.removeAllUnits(player_.getColor());
                }
            }
        }
        UnitHolder space = tile.getUnitHolders().get(Constants.SPACE);
        space.removeAllTokens();
        activeGame.removeTile(tile.getPosition());

        //Add the muaat supernova to the map and copy over the space unitholder
        Tile novaTile = new Tile(AliasHandler.resolveTile("81"), tile.getPosition(), space);
        activeGame.setTile(novaTile);
    }

}
