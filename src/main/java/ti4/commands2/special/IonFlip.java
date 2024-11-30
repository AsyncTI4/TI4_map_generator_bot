package ti4.commands2.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.image.TileHelper;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;

class IonFlip extends GameStateSubcommand {

    public IonFlip() {
        super(Constants.ION_TOKEN_FLIP, "Flip ION Storm Token", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

        OptionMapping tileOption = event.getOption(Constants.TILE_NAME);
        if (tileOption == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Specify a tile");
            return;
        }
        String tileID = AliasHandler.resolveTile(tileOption.getAsString().toLowerCase());
        Tile tile = TileHelper.getTile(event, tileID, game);
        if (tile == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not resolve tileID:  `" + tileID + "`. Tile not found");
            return;
        }
        UnitHolder spaceUnitHolder = tile.getUnitHolders().get(Constants.SPACE);
        if (spaceUnitHolder == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No valid Space found");
            return;
        }
        if (spaceUnitHolder.getTokenList().contains(Constants.TOKEN_ION_ALPHA_PNG)) {
            tile.removeToken(Constants.TOKEN_ION_ALPHA_PNG, spaceUnitHolder.getName());
            tile.addToken(Constants.TOKEN_ION_BETA_PNG, spaceUnitHolder.getName());
        } else if (spaceUnitHolder.getTokenList().contains(Constants.TOKEN_ION_BETA_PNG)) {
            tile.removeToken(Constants.TOKEN_ION_BETA_PNG, spaceUnitHolder.getName());
            tile.addToken(Constants.TOKEN_ION_ALPHA_PNG, spaceUnitHolder.getName());
        }
    }
}
