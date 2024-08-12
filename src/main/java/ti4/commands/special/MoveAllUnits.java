package ti4.commands.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.uncategorized.ShowGame;
import ti4.commands.units.AddRemoveUnits;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.helpers.Units.UnitKey;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;

public class MoveAllUnits extends SpecialSubcommandData {
    public MoveAllUnits() {
        super(Constants.MOVE_ALL_UNITS, "Move All Units From One System To Another");
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name to move from").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME_TO, "System/Tile name to move to").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        OptionMapping tileOption = event.getOption(Constants.TILE_NAME);
        if (tileOption == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Specify a tile");
            return;
        }

        OptionMapping tileOptionTo = event.getOption(Constants.TILE_NAME_TO);
        if (tileOptionTo == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Specify a tile");
            return;
        }
        String tile1ID = AliasHandler.resolveTile(tileOption.getAsString().toLowerCase());
        Tile tile1 = AddRemoveUnits.getTile(event, tile1ID, game);
        if (tile1 == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not resolve tileID:  `" + tile1ID + "`. Tile not found");
            return;
        }

        String tile2ID = AliasHandler.resolveTile(tileOptionTo.getAsString().toLowerCase());
        Tile tile2 = AddRemoveUnits.getTile(event, tile2ID, game);

        UnitHolder space = tile2.getUnitHolders().get("space");
        for (UnitHolder uH : tile1.getUnitHolders().values()) {
            for (UnitKey key : uH.getUnits().keySet()) {
                space.addUnit(key, uH.getUnits().get(key));
            }
            for (UnitKey key : uH.getUnitDamage().keySet()) {
                space.addUnitDamage(key, uH.getUnitDamage().get(key));
            }
            for (Player p : game.getRealPlayers()) {
                uH.removeAllUnits(p.getColor());
            }
        }

        ShowGame.simpleShowGame(game, event, DisplayType.map);
    }
}
