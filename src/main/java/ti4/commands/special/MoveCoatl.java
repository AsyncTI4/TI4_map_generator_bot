package ti4.commands.special;

import org.apache.commons.lang3.StringUtils;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.image.TileHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;

class MoveCoatl extends GameStateSubcommand {

    public MoveCoatl() {
        super(Constants.MOVE_COATL, "Moves the coatl token.", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "Target System/Tile name").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();

        String tileName = StringUtils.substringBefore(event.getOption(Constants.TILE_NAME).getAsString().toLowerCase(), " ");
        Tile tile = TileHelper.getTile(event, tileName, game);
        if (tile == null) {
            MessageHelper.sendMessageToEventChannel(event, "Could not resolve tileID:  `" + tileName + "`. Tile not found");
            return;
        }

        String tokenName = "custc1";
        if (ButtonHelper.isCoatlHealed(game)) {
            tokenName = "custvpc1";
        }
        tokenName = AliasHandler.resolveToken(tokenName);

        StringBuilder sb = new StringBuilder(player.getRepresentation());
        tile.addToken(Mapper.getTokenID(tokenName), Constants.SPACE);
        sb.append(" moved the coatl to ").append(tile.getRepresentation());
        for (Tile tile_ : game.getTileMap().values()) {
            if (!tile.equals(tile_) && tile_.removeToken(Mapper.getTokenID(tokenName), Constants.SPACE)) {
                sb.append(" (from ").append(tile_.getRepresentation()).append(")");
                break;
            }
        }
        MessageHelper.sendMessageToEventChannel(event, sb.toString());
    }

}
