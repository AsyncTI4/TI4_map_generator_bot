package ti4.commands.ds;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.units.AddRemoveUnits;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;

public class ZelianHero extends DiscordantStarsSubcommandData {

    public ZelianHero() {
        super(Constants.ZELIAN_HERO, "Celestial Impact a system (replace with Zelian Asteroid field)");
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color using Zelian Hero 'Cataclysm - Celestial Impact'").setAutoComplete(true));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.DESTROY_OTHER_UNITS, "True to also destroy other players' ground force units on planets"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
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

        //Remove all other players ground force units from the tile in question
        OptionMapping destroyOption = event.getOption(Constants.DESTROY_OTHER_UNITS);
        if (destroyOption == null || destroyOption.getAsBoolean()) {
            for (Player player_ : activeGame.getPlayers().values()) {
                if (player_ != player) {
                    for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                        if (!unitHolder.getName().equals(Constants.SPACE)) {
                            unitHolder.removeAllUnits(player_.getColor());
                        }
                    }
                }
            }
        }
        UnitHolder space = tile.getUnitHolders().get(Constants.SPACE);
        activeGame.removeTile(tile.getPosition());

        //Add the zelian asteroid field to the map and copy over the space unitholder
        Tile asteroidTile = new Tile(AliasHandler.resolveTile("D36"), tile.getPosition(), space);
        activeGame.setTile(asteroidTile);
    }
}
